package com.abdullah.msal2.importer;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The export file (R-IO-01, API.md section 6): M-SAL's own format ({@code schemaVersion: 3}),
 * so the old app can open it too, with M-SAL2.0's UUIDs as ids. Deleted budgets are left out;
 * deleted entries are kept with their {@code deletedAt}, as in M-SAL.
 */
@Service
public class ExportService {

	public static final String APP = "M-SAL2.0";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private final NamedParameterJdbcTemplate jdbc;
	private final Clock clock;

	public ExportService(NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Everything the user has, as an M-SAL file. Changes nothing. */
	@Transactional(readOnly = true)
	public ObjectNode export(UUID userId) {
		MapSqlParameterSource user = new MapSqlParameterSource("userId", userId);
		List<ObjectNode> profiles = new ArrayList<>();
		Map<UUID, ObjectNode> dataById = new LinkedHashMap<>();
		jdbc.query("""
				select id, name, month_label, salary_label, salary_amount, salary_is_approx, last_account_id
				from msal.budgets where user_id = :userId and deleted_at is null
				order by sort_order, created_at
				""", user, rs -> {
			ObjectNode data = JSON.objectNode()
					.put("monthLabel", rs.getString("month_label"))
					.put("salaryLabel", rs.getString("salary_label"))
					.put("salary", LedgerAmounts.raw(rs.getBigDecimal("salary_amount"), rs.getBoolean("salary_is_approx")));
			data.putArray("blocks");
			ObjectNode exp = data.putObject("exp");
			exp.putArray("accounts");
			exp.put("lastAccountId", text(rs.getObject("last_account_id")));
			exp.putArray("categories");
			exp.putArray("entries");
			UUID id = rs.getObject("id", UUID.class);
			dataById.put(id, data);
			ObjectNode profile = JSON.objectNode().put("id", id.toString()).put("name", rs.getString("name"));
			profile.set("data", data);
			profiles.add(profile);
		});

		Map<UUID, ArrayNode> rowsByGroup = new LinkedHashMap<>();
		jdbc.query("""
				select g.id, g.budget_id, g.title from msal.budget_groups g
				join msal.budgets b on b.id = g.budget_id and b.deleted_at is null
				where g.user_id = :userId order by g.sort_order, g.created_at
				""", user, rs -> {
			ObjectNode data = dataById.get(rs.getObject("budget_id", UUID.class));
			ObjectNode block = ((ArrayNode) data.get("blocks")).addObject()
					.put("id", rs.getString("id")).put("title", rs.getString("title"));
			rowsByGroup.put(rs.getObject("id", UUID.class), block.putArray("rows"));
		});
		jdbc.query("""
				select id, group_id, name, amount, is_approx, is_done from msal.budget_items
				where user_id = :userId order by sort_order, created_at
				""", user, rs -> {
			ArrayNode rows = rowsByGroup.get(rs.getObject("group_id", UUID.class));
			if (rows != null) {
				rows.addObject().put("id", rs.getString("id")).put("name", rs.getString("name"))
						.put("amount", LedgerAmounts.raw(rs.getBigDecimal("amount"), rs.getBoolean("is_approx")))
						.put("done", rs.getBoolean("is_done"));
			}
		});

		Map<UUID, ArrayNode> cyclesByAccount = new LinkedHashMap<>();
		jdbc.query("""
				select id, budget_id, name, is_archived from msal.accounts
				where user_id = :userId order by sort_order, created_at
				""", user, rs -> {
			ObjectNode exp = exp(dataById, rs);
			if (exp == null) {
				return;
			}
			ObjectNode account = ((ArrayNode) exp.get("accounts")).addObject()
					.put("id", rs.getString("id")).put("name", rs.getString("name"))
					.put("archived", rs.getBoolean("is_archived"));
			cyclesByAccount.put(rs.getObject("id", UUID.class), account.putArray("cycles"));
		});
		jdbc.query("""
				select id, account_id, start_date, allowance, expected_next_salary_date from msal.salary_cycles
				where user_id = :userId order by start_date
				""", user, rs -> {
			ArrayNode cycles = cyclesByAccount.get(rs.getObject("account_id", UUID.class));
			if (cycles == null) {
				return;
			}
			ObjectNode cycle = cycles.addObject().put("id", rs.getString("id"))
					.put("startDate", rs.getString("start_date"))
					.put("allowance", number(rs.getBigDecimal("allowance")));
			if (rs.getString("expected_next_salary_date") != null) {
				cycle.put("expectedNextSalaryDate", rs.getString("expected_next_salary_date"));
			}
		});
		jdbc.query("""
				select id, budget_id, code, name, emoji, is_hidden from msal.categories
				where user_id = :userId order by sort_order, created_at
				""", user, rs -> {
			ObjectNode exp = exp(dataById, rs);
			if (exp != null) {
				ObjectNode category = ((ArrayNode) exp.get("categories")).addObject().put("id", rs.getString("id"))
						.put("name", rs.getString("name")).put("emoji", rs.getString("emoji"))
						.put("hidden", rs.getBoolean("is_hidden"));
				if (rs.getString("code") != null) {
					category.put("code", rs.getString("code")); // M-SAL keeps unknown fields
				}
			}
		});
		jdbc.query("""
				select id, budget_id, account_id, category_id, type, amount, name, entry_date,
				       created_at, updated_at, deleted_at
				from msal.entries where user_id = :userId order by entry_date, created_at
				""", user, rs -> {
			ObjectNode exp = exp(dataById, rs);
			if (exp == null) {
				return;
			}
			ObjectNode entry = ((ArrayNode) exp.get("entries")).addObject().put("id", rs.getString("id"))
					.put("type", "INCOME".equals(rs.getString("type")) ? "income" : "expense")
					.put("amount", number(rs.getBigDecimal("amount"))).put("name", rs.getString("name"))
					.put("categoryId", rs.getString("category_id")).put("date", rs.getString("entry_date"))
					.put("accountId", rs.getString("account_id"))
					.put("createdAt", millis(rs.getTimestamp("created_at")))
					.put("updatedAt", millis(rs.getTimestamp("updated_at")));
			if (rs.getTimestamp("deleted_at") != null) {
				entry.put("deletedAt", millis(rs.getTimestamp("deleted_at")));
			}
		});

		for (ObjectNode data : dataById.values()) {
			ObjectNode exp = (ObjectNode) data.get("exp");
			ArrayNode accounts = (ArrayNode) exp.get("accounts");
			if (exp.get("lastAccountId").isNull() && !accounts.isEmpty()) {
				exp.put("lastAccountId", firstOpen(accounts));
			}
		}

		ObjectNode root = JSON.objectNode().put("schemaVersion", ImportParser.SCHEMA_VERSION).put("app", APP)
				.put("exportedAt", Instant.now(clock).toString());
		UUID last = jdbc.query("select last_budget_id from msal.app_users where id = :userId", user,
				rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
		root.put("currentId", last != null && dataById.containsKey(last) ? last.toString()
				: profiles.isEmpty() ? null : profiles.getFirst().get("id").asString());
		root.putArray("profiles").addAll(profiles);
		return root;
	}

	private static ObjectNode exp(Map<UUID, ObjectNode> dataById, ResultSet rs) throws SQLException {
		ObjectNode data = dataById.get(rs.getObject("budget_id", UUID.class));
		return data == null ? null : (ObjectNode) data.get("exp");
	}

	private static String firstOpen(ArrayNode accounts) {
		for (var a : accounts) {
			if (!a.get("archived").booleanValue()) {
				return a.get("id").asString();
			}
		}
		return accounts.get(0).get("id").asString();
	}

	private static String text(Object value) {
		return value == null ? null : value.toString();
	}

	/** A JSON number as M-SAL keeps it ({@code 46.25}, {@code 3000}). */
	private static BigDecimal number(BigDecimal value) {
		return value.signum() == 0 ? BigDecimal.ZERO : new BigDecimal(value.stripTrailingZeros().toPlainString());
	}

	private static long millis(Timestamp t) {
		return t.toInstant().toEpochMilli();
	}
}
