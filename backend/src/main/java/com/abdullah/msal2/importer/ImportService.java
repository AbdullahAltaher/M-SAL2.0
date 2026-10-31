package com.abdullah.msal2.importer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.importer.ImportDtos.Target;
import com.abdullah.msal2.importer.ImportFile.Account;
import com.abdullah.msal2.importer.ImportFile.Block;
import com.abdullah.msal2.importer.ImportFile.Category;
import com.abdullah.msal2.importer.ImportFile.Cycle;
import com.abdullah.msal2.importer.ImportFile.Data;
import com.abdullah.msal2.importer.ImportFile.Entry;
import com.abdullah.msal2.importer.ImportFile.Exp;
import com.abdullah.msal2.importer.ImportFile.Kind;
import com.abdullah.msal2.importer.ImportFile.Profile;
import com.abdullah.msal2.importer.ImportFile.Row;
import com.abdullah.msal2.importer.ImportParser.Counts;
import com.abdullah.msal2.importer.ImportParser.Result;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Applies an import file (R-IO-03, DATABASE.md 6.3) in one transaction, after saving all the
 * user's data in {@code audit_log.before_data} (R-IO-05) and the original file in
 * {@code imports.raw_data} (D-09).
 * <p>
 * A file record is the same as a saved one when its id is that record's id or
 * {@code legacy_id} in the same place (API.md section 6); a default category also matches
 * by its code ("cat_food"). A new record keeps the file's id when it is a free UUID, else gets
 * a new one and keeps the file's id as {@code legacy_id}.
 * <ul>
 * <li><b>MERGE</b>, "current wins": nothing saved is changed or removed; missing budgets,
 * groups, items, accounts, cycles (not on a date the account already has), categories and
 * entries are added.</li>
 * <li><b>REPLACE</b>, "the file wins": each budget becomes the file's, and budgets not in the
 * file are deleted (soft). A budget whose file part has no Expenses keeps its own.</li>
 * <li>A one-person file (SINGLE) goes into the current budget, and never touches its
 * Expenses on REPLACE, as in M-SAL.</li>
 * </ul>
 */
@Service
public class ImportService {

	public enum Mode {
		MERGE, REPLACE
	}

	private final JdbcTemplate jdbc;
	private final ExportService export;
	private final AuditService audit;
	private final JsonMapper json;
	private final Clock clock;

	public ImportService(JdbcTemplate jdbc, ExportService export, AuditService audit, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.export = export;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	/** The import's id. */
	@Transactional
	public UUID apply(CurrentUser user, Result parsed, Mode mode, String fileName, byte[] content) {
		Instant now = Instant.now(clock);
		Object before = export.export(user.id());
		Run run = new Run(user.id(), Timestamp.from(now));
		ImportFile file = parsed.file();
		if (file.kind() == Kind.SINGLE) {
			run.single(file.profiles().getFirst().data(), mode);
		}
		else {
			run.state(file, mode);
		}
		UUID importId = jdbc.queryForObject("""
				insert into msal.imports (user_id, file_name, mode, raw_data, counts, warnings, created_at)
				values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?) returning id
				""", UUID.class, user.id(), fileName, mode.name(), new String(content, StandardCharsets.UTF_8),
				json.writeValueAsString(parsed.counts()), json.writeValueAsString(file.warnings().list()),
				Timestamp.from(now));
		audit.record(user.id(), AuditAction.IMPORT, "import", importId, before,
				Map.of("mode", mode.name(), "kind", file.kind().name(), "counts", parsed.counts()));
		return importId;
	}

	/** What the user has now, counted like the file (M-SAL's countState). */
	@Transactional(readOnly = true)
	public Counts current(UUID userId) {
		return jdbc.queryForObject("""
				with b as (select id from msal.budgets where user_id = ? and deleted_at is null),
				     a as (select id from msal.accounts where budget_id in (select id from b))
				select (select count(*) from b),
				       (select count(*) from msal.budget_groups where budget_id in (select id from b)),
				       (select count(*) from msal.budget_items i join msal.budget_groups g on g.id = i.group_id
				        where g.budget_id in (select id from b)),
				       (select count(*) from a),
				       (select count(*) from msal.salary_cycles where account_id in (select id from a)),
				       (select count(*) from msal.entries where budget_id in (select id from b) and deleted_at is null),
				       (select count(*) from msal.entries where budget_id in (select id from b) and deleted_at is not null)
				""", (rs, n) -> new Counts(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
				rs.getInt(6), rs.getInt(7)), userId);
	}

	/** The budget a one-person file goes into: the last opened one, else the first. */
	@Transactional(readOnly = true)
	public Target currentBudget(UUID userId) {
		return jdbc.query("""
				select b.id, b.name from msal.budgets b join msal.app_users u on u.id = b.user_id
				where b.user_id = ? and b.deleted_at is null
				order by coalesce(b.id = u.last_budget_id, false) desc, b.sort_order, b.created_at limit 1
				""", (rs, n) -> new Target(rs.getObject(1, UUID.class), rs.getString(2)), userId)
				.stream().findFirst().orElse(null);
	}


	/** One import: the user, its time and the ids taken so far. */
	private final class Run {

		private final UUID userId;
		private final Timestamp now;

		Run(UUID userId, Timestamp now) {
			this.userId = userId;
			this.now = now;
		}

		void state(ImportFile file, Mode mode) {
			Map<String, UUID> saved = index("select id, legacy_id from msal.budgets where user_id = ? and deleted_at is null",
					userId);
			Set<UUID> kept = new HashSet<>();
			Map<String, UUID> byFileId = new HashMap<>();
			int order = mode == Mode.REPLACE ? 0
					: jdbc.queryForObject("select coalesce(max(sort_order), 0) from msal.budgets where user_id = ?",
							Integer.class, userId);
			for (Profile p : file.profiles()) {
				UUID budgetId = saved.get(p.id());
				if (budgetId != null && !kept.add(budgetId)) {
					budgetId = null; // two file people matching one budget: the second is new
				}
				if (budgetId == null) {
					budgetId = insertBudget(p, ++order);
					kept.add(budgetId);
					insertBlocks(budgetId, p.data().blocks());
					if (p.data().exp() != null) {
						insertExp(budgetId, p.data().exp());
					}
					else {
						defaultExp(budgetId);
					}
				}
				else if (mode == Mode.MERGE) {
					mergeBlocks(budgetId, p.data().blocks());
					if (p.data().exp() != null) {
						mergeExp(budgetId, p.data().exp());
					}
				}
				else {
					jdbc.update("""
							update msal.budgets set name = ?, sort_order = ?, version = version + 1, updated_at = ?
							where id = ?
							""", p.name(), ++order, now, budgetId);
					replaceLedger(budgetId, p.data());
					if (p.data().exp() != null) {
						jdbc.update("update msal.budgets set last_account_id = null where id = ?", budgetId);
						jdbc.update("delete from msal.entries where budget_id = ?", budgetId);
						jdbc.update("delete from msal.accounts where budget_id = ?", budgetId);
						jdbc.update("delete from msal.categories where budget_id = ?", budgetId);
						insertExp(budgetId, p.data().exp());
					}
				}
				byFileId.put(p.id(), budgetId);
			}
			if (mode == Mode.REPLACE) {
				for (UUID id : Set.copyOf(saved.values())) {
					if (!kept.contains(id)) {
						jdbc.update("update msal.budgets set deleted_at = ?, version = version + 1, updated_at = ? where id = ?",
								now, now, id);
					}
				}
				jdbc.update("update msal.app_users set last_budget_id = ? where id = ?", byFileId.get(file.currentId()),
						userId);
			}
			else {
				jdbc.update("update msal.app_users set last_budget_id = ? where id = ? and last_budget_id is null",
						byFileId.get(file.currentId()), userId);
			}
		}

		void single(Data data, Mode mode) {
			Target target = currentBudget(userId);
			if (mode == Mode.MERGE) {
				mergeBlocks(target.id(), data.blocks());
				if (data.exp() != null) {
					mergeExp(target.id(), data.exp());
				}
			}
			else {
				replaceLedger(target.id(), data);
			}
		}

		private UUID insertBudget(Profile p, int order) {
			Ids ids = ids("budgets", p.id());
			if (ids.legacy() != null) {
				// a deleted budget may still hold this legacy id (the unique key covers it)
				jdbc.update("update msal.budgets set legacy_id = null where user_id = ? and legacy_id = ? and deleted_at is not null",
						userId, ids.legacy());
			}
			Data d = p.data();
			jdbc.update("""
					insert into msal.budgets (id, user_id, name, month_label, salary_label, salary_amount, salary_is_approx,
					    sort_order, legacy_id, created_at, updated_at)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", ids.id(), userId, p.name(), d.monthLabel(), d.salaryLabel(), d.salary().value(),
					d.salary().approx(), order, ids.legacy(), now, now);
			return ids.id();
		}

		private void replaceLedger(UUID budgetId, Data d) {
			jdbc.update("""
					update msal.budgets set month_label = ?, salary_label = ?, salary_amount = ?, salary_is_approx = ?,
					    version = version + 1, updated_at = ?
					where id = ?
					""", d.monthLabel(), d.salaryLabel(), d.salary().value(), d.salary().approx(), now, budgetId);
			jdbc.update("delete from msal.budget_groups where budget_id = ?", budgetId);
			insertBlocks(budgetId, d.blocks());
		}

		private void insertBlocks(UUID budgetId, List<Block> blocks) {
			int order = 0;
			for (Block b : blocks) {
				UUID groupId = insertGroup(budgetId, b, ++order);
				insertRows(groupId, b.rows(), 0);
			}
		}

		private void mergeBlocks(UUID budgetId, List<Block> blocks) {
			Map<String, UUID> saved = index("select id, legacy_id from msal.budget_groups where budget_id = ?", budgetId);
			int order = max("budget_groups", "budget_id", budgetId);
			for (Block b : blocks) {
				UUID groupId = saved.get(b.id());
				if (groupId == null) {
					insertRows(insertGroup(budgetId, b, ++order), b.rows(), 0);
					continue;
				}
				Map<String, UUID> rows = index("select id, legacy_id from msal.budget_items where group_id = ?", groupId);
				insertRows(groupId, b.rows().stream().filter(r -> !rows.containsKey(r.id())).toList(),
						max("budget_items", "group_id", groupId));
			}
		}

		private UUID insertGroup(UUID budgetId, Block b, int order) {
			Ids ids = ids("budget_groups", b.id());
			jdbc.update("""
					insert into msal.budget_groups (id, user_id, budget_id, title, sort_order, legacy_id, created_at, updated_at)
					values (?, ?, ?, ?, ?, ?, ?, ?)
					""", ids.id(), userId, budgetId, b.title(), order, ids.legacy(), now, now);
			return ids.id();
		}

		private void insertRows(UUID groupId, List<Row> rows, int order) {
			for (Row r : rows) {
				Ids ids = ids("budget_items", r.id());
				jdbc.update("""
						insert into msal.budget_items (id, user_id, group_id, name, amount, is_approx, is_done, sort_order,
						    legacy_id, created_at, updated_at)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", ids.id(), userId, groupId, r.name(), r.amount().value(), r.amount().approx(), r.done(),
						++order, ids.legacy(), now, now);
			}
		}

		/** A new budget without an Expenses part starts like any new one (R-BUD-06). */
		private void defaultExp(UUID budgetId) {
			UUID accountId = UUID.randomUUID();
			jdbc.update("""
					insert into msal.accounts (id, user_id, budget_id, name, sort_order, created_at, updated_at)
					values (?, ?, ?, ?, 1, ?, ?)
					""", accountId, userId, budgetId, ImportParser.DEFAULT_ACCOUNT_NAME, now, now);
			jdbc.update("""
					insert into msal.categories (user_id, budget_id, code, name, emoji, sort_order, created_at, updated_at)
					select ?, ?, code, name_ar, emoji, sort_order, ?, ? from msal.category_templates
					""", userId, budgetId, now, now);
			jdbc.update("update msal.budgets set last_account_id = ? where id = ?", accountId, budgetId);
		}

		private void insertExp(UUID budgetId, Exp exp) {
			mergeExp(budgetId, exp);
		}

		/**
		 * Adds the missing accounts, cycles, categories and entries. On an empty budget (a new
		 * one, or after REPLACE cleared it) this inserts all of them.
		 */
		private void mergeExp(UUID budgetId, Exp exp) {
			Map<String, UUID> accounts = index("select id, legacy_id from msal.accounts where budget_id = ?", budgetId);
			int order = max("accounts", "budget_id", budgetId);
			for (Account a : exp.accounts()) {
				UUID accountId = accounts.get(a.id());
				if (accountId == null) {
					Ids ids = ids("accounts", a.id());
					jdbc.update("""
							insert into msal.accounts (id, user_id, budget_id, name, is_archived, sort_order, legacy_id,
							    created_at, updated_at)
							values (?, ?, ?, ?, ?, ?, ?, ?, ?)
							""", ids.id(), userId, budgetId, a.name(), a.archived(), ++order, ids.legacy(), now, now);
					accountId = ids.id();
					accounts.put(a.id(), accountId);
				}
				mergeCycles(accountId, a.cycles());
			}

			Map<String, UUID> categories = index("select id, legacy_id from msal.categories where budget_id = ?", budgetId);
			Set<String> codes = new HashSet<>();
			jdbc.query("select id, code from msal.categories where budget_id = ? and code is not null", rs -> {
				codes.add(rs.getString(2));
				categories.putIfAbsent(rs.getString(2), rs.getObject(1, UUID.class));
			}, budgetId);
			Set<String> templates = new HashSet<>(jdbc.queryForList("select code from msal.category_templates", String.class));
			int categoryOrder = max("categories", "budget_id", budgetId);
			for (Category c : exp.categories()) {
				if (categories.containsKey(c.id())) {
					continue;
				}
				if (c.code() != null && categories.containsKey(c.code())) {
					// the same default category under another id (another account's export)
					categories.put(c.id(), categories.get(c.code()));
					continue;
				}
				Ids ids = ids("categories", c.id());
				String code = c.code() != null && templates.contains(c.code()) && codes.add(c.code()) ? c.code() : null;
				jdbc.update("""
						insert into msal.categories (id, user_id, budget_id, code, name, emoji, is_hidden, sort_order, legacy_id,
						    created_at, updated_at)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", ids.id(), userId, budgetId, code, c.name(), c.emoji(), c.hidden(), ++categoryOrder,
						ids.legacy(), now, now);
				categories.put(c.id(), ids.id());
				if (code != null) {
					categories.putIfAbsent(code, ids.id());
				}
			}

			Map<String, UUID> entries = index("select id, legacy_id from msal.entries where budget_id = ?", budgetId);
			for (Entry e : exp.entries()) {
				if (entries.containsKey(e.id())) {
					continue;
				}
				Ids ids = ids("entries", e.id());
				Timestamp created = e.createdAt() == null ? now : Timestamp.from(e.createdAt());
				Timestamp updated = e.updatedAt() == null ? created : Timestamp.from(e.updatedAt());
				jdbc.update("""
						insert into msal.entries (id, user_id, budget_id, account_id, category_id, type, amount, name,
						    entry_date, legacy_id, created_at, updated_at, deleted_at)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", ids.id(), userId, budgetId, accounts.get(e.accountId()), categories.get(e.categoryId()),
						e.income() ? "INCOME" : "EXPENSE", e.amount(), e.name(), e.date(), ids.legacy(), created, updated,
						e.deletedAt() == null ? null : Timestamp.from(e.deletedAt()));
				entries.put(e.id(), ids.id());
			}

			jdbc.update("update msal.budgets set last_account_id = ? where id = ? and last_account_id is null",
					accounts.get(exp.lastAccountId()), budgetId);
		}

		/** Cycles not saved yet, and not on a date the account already has (R-IO-03). */
		private void mergeCycles(UUID accountId, List<Cycle> cycles) {
			Map<String, UUID> saved = index("select id, legacy_id from msal.salary_cycles where account_id = ?", accountId);
			Set<String> starts = new HashSet<>(jdbc.queryForList(
					"select start_date::text from msal.salary_cycles where account_id = ?", String.class, accountId));
			for (Cycle c : cycles) {
				if (saved.containsKey(c.id()) || !starts.add(c.startDate().toString())) {
					continue;
				}
				Ids ids = ids("salary_cycles", c.id());
				jdbc.update("""
						insert into msal.salary_cycles (id, user_id, account_id, start_date, allowance,
						    expected_next_salary_date, legacy_id, created_at, updated_at)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", ids.id(), userId, accountId, c.startDate(), c.allowance(), c.expectedNextSalaryDate(),
						ids.legacy(), now, now);
			}
		}

		/** Saved records by id and by legacy id; an id wins over another record's legacy id. */
		private Map<String, UUID> index(String sql, Object scope) {
			Map<String, UUID> map = new HashMap<>();
			Map<String, UUID> legacy = new HashMap<>();
			jdbc.query(sql, rs -> {
				UUID id = rs.getObject(1, UUID.class);
				map.put(id.toString(), id);
				if (rs.getString(2) != null) {
					legacy.put(rs.getString(2), id);
				}
			}, scope);
			legacy.forEach(map::putIfAbsent);
			return map;
		}

		private int max(String table, String scope, UUID scopeId) {
			return jdbc.queryForObject("select coalesce(max(sort_order), 0) from msal." + table + " where " + scope + " = ?",
					Integer.class, scopeId);
		}

		/** The new record's id, and the file id to keep as legacy id when it cannot be the id. */
		private Ids ids(String table, String fileId) {
			if (fileId.startsWith(ImportFile.GENERATED)) {
				return new Ids(UUID.randomUUID(), null);
			}
			UUID uuid = uuid(fileId);
			if (uuid != null && !Boolean.TRUE.equals(jdbc.queryForObject(
					"select exists (select 1 from msal." + table + " where id = ?)", Boolean.class, uuid))) {
				return new Ids(uuid, null);
			}
			return new Ids(UUID.randomUUID(), fileId);
		}

		private static UUID uuid(String s) {
			if (s.length() != 36) {
				return null;
			}
			try {
				UUID u = UUID.fromString(s);
				return u.toString().equals(s.toLowerCase()) ? u : null;
			}
			catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	private record Ids(UUID id, String legacy) {
	}
}
