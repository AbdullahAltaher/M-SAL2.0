package com.abdullah.msal2.importer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.abdullah.msal2.budget.MonthLabel;
import com.abdullah.msal2.importer.ImportFile.Account;
import com.abdullah.msal2.importer.ImportFile.Amount;
import com.abdullah.msal2.importer.ImportFile.Block;
import com.abdullah.msal2.importer.ImportFile.Category;
import com.abdullah.msal2.importer.ImportFile.Cycle;
import com.abdullah.msal2.importer.ImportFile.Data;
import com.abdullah.msal2.importer.ImportFile.Entry;
import com.abdullah.msal2.importer.ImportFile.Exp;
import com.abdullah.msal2.importer.ImportFile.Kind;
import com.abdullah.msal2.importer.ImportFile.Profile;
import com.abdullah.msal2.importer.ImportFile.Row;
import com.abdullah.msal2.importer.Warnings.Code;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.money.Money;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Reads an M-SAL or M-SAL2.0 file (R-IO-02), sanitizing it the way M-SAL's parseImport,
 * sanitizeState, sanitizeData and sanitizeExp do, then fixing what the database cannot hold
 * (DATABASE.md 6.2). Counts follow M-SAL's countState. Saves nothing.
 */
@Component
public class ImportParser {

	public static final int SCHEMA_VERSION = 3;

	public static final String DEFAULT_ACCOUNT_ID = "acc_main";
	public static final String DEFAULT_ACCOUNT_NAME = "مصاريفي";
	public static final String OTHER_CATEGORY_ID = "cat_other";
	private static final String DEFAULT_EMOJI = "📦";

	/** M-SAL's DEFAULT_CATEGORIES, the same as V5's category_templates. */
	public static final List<Category> DEFAULT_CATEGORIES = List.of(
			cat("cat_food", "أكل ومطاعم", "🍔"), cat("cat_grocery", "بقالة", "🛒"),
			cat("cat_fuel", "بنزين ومواصلات", "⛽"), cat("cat_bills", "فواتير", "🧾"),
			cat("cat_electricity", "كهرباء", "⚡"), cat("cat_water", "ماء", "💧"),
			cat("cat_telecom", "اتصالات وإنترنت", "📱"), cat("cat_rent", "إيجار", "🏠"),
			cat("cat_shopping", "ملابس وتسوق", "👕"), cat("cat_health", "صحة", "💊"),
			cat("cat_fun", "ترفيه", "🎮"), cat("cat_coffee", "قهوة وكافيهات", "☕"),
			cat("cat_travel", "سفر", "✈️"), cat("cat_edu", "تعليم", "🎓"),
			cat("cat_kids", "أطفال", "🧸"), cat("cat_gifts", "هدايا", "🎁"),
			cat("cat_charity", "تبرعات وصدقة", "🤝"), cat("cat_care", "عناية شخصية", "🧼"),
			cat("cat_pets", "حيوانات أليفة", "🐕"), cat(OTHER_CATEGORY_ID, "أخرى", DEFAULT_EMOJI));

	private static final Set<String> DEFAULT_CODES = Set.copyOf(DEFAULT_CATEGORIES.stream().map(Category::id).toList());

	private static final Pattern DATE =Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
	private static final BigDecimal MAX_ENTRY = new BigDecimal("999999999");

	private final JsonMapper mapper;

	public ImportParser(JsonMapper mapper) {
		this.mapper = mapper;
	}

	/** The parsed file and its counts, or SRV-007-E001 / SRV-007-E002. */
	public record Result(ImportFile file, Counts counts) {
	}

	/** M-SAL's countState, shown in the preview next to the current data. */
	public record Counts(int profiles, int blocks, int rows, int accounts, int cycles, int entries, int deleted) {
	}

	public Result parse(byte[] content, ZoneId zone, Instant now) {
		JsonNode root;
		try {
			root = mapper.readTree(content);
		}
		catch (JacksonException e) {
			throw new ApiException(ErrorCode.SRV_007_E001);
		}
		if (root == null || root.isMissingNode()) {
			throw new ApiException(ErrorCode.SRV_007_E001);
		}
		return new Run(zone, now).file(root);
	}

	/** One parse: its id generator, its warnings and its counts. */
	private static final class Run {

		private final ZoneId zone;
		private final Instant now;
		private final LocalDate today;
		private final Warnings warnings = new Warnings();
		private int generated;
		private int profiles, blocks, rows, accounts, cycles, entries, deleted;

		Run(ZoneId zone, Instant now) {
			this.zone = zone;
			this.now = now;
			this.today = LocalDate.ofInstant(now, zone);
		}

		Result file(JsonNode root) {
			if (!root.isObject()) {
				throw new ApiException(ErrorCode.SRV_007_E002);
			}
			JsonNode list = root.get("profiles");
			if (list != null && list.isArray() && hasObject(list)) {
				JsonNode version = root.get("schemaVersion");
				boolean newer = version != null && version.isNumber() && version.doubleValue() > SCHEMA_VERSION;
				Set<String> ids = new HashSet<>();
				List<Profile> people = new ArrayList<>();
				for (JsonNode p : list) {
					if (!p.isObject()) {
						continue;
					}
					profiles++;
					String id = uniqueId(p.get("id"), ids);
					String name = text(p.get("name"));
					name = name != null && !name.isBlank() ? name : "بدون اسم";
					people.add(new Profile(id, name, data(p.get("data"), newer)));
				}
				String currentId = text(root.get("currentId"));
				String current = people.stream().anyMatch(p -> p.id().equals(currentId)) ? currentId
						: people.getFirst().id();
				return result(new ImportFile(Kind.STATE, newer, current, people, warnings));
			}
			JsonNode blocks = root.get("blocks");
			if (blocks != null && blocks.isArray()) {
				profiles++;
				Profile single = new Profile(null, null, data(root, false));
				return result(new ImportFile(Kind.SINGLE, false, null, List.of(single), warnings));
			}
			throw new ApiException(ErrorCode.SRV_007_E002);
		}

		private Result result(ImportFile file) {
			return new Result(file, new Counts(profiles, blocks, rows, accounts, cycles, entries, deleted));
		}

		/**
		 * @param newer a newer file's {@code exp} is not reshaped by M-SAL, so a missing one is
		 *              not counted as the default account
		 */
		private Data data(JsonNode d, boolean newer) {
			if (d == null || !d.isObject()) {
				d = JsonNodeFactory.instance.objectNode();
			}
			String monthLabel = text(d.get("monthLabel"));
			String salaryLabel = text(d.get("salaryLabel"));
			List<Block> list = new ArrayList<>();
			Set<String> blockIds = new HashSet<>();
			JsonNode bs = d.get("blocks");
			if (bs != null && bs.isArray()) {
				for (JsonNode b : bs) {
					if (!b.isObject()) {
						continue;
					}
					blocks++;
					String title = text(b.get("title"));
					List<Row> items = new ArrayList<>();
					Set<String> rowIds = new HashSet<>();
					JsonNode rs = b.get("rows");
					if (rs != null && rs.isArray()) {
						for (JsonNode r : rs) {
							if (!r.isObject()) {
								continue;
							}
							rows++;
							String name = text(r.get("name"));
							items.add(new Row(uniqueId(r.get("id"), rowIds), name == null ? "" : name,
									ledgerAmount(r.get("amount")), truthy(r.get("done"))));
						}
					}
					list.add(new Block(uniqueId(b.get("id"), blockIds), title == null ? "" : title, items));
				}
			}
			JsonNode e = d.get("exp");
			Exp exp = null;
			if (e != null && e.isObject()) {
				exp = exp(e, newer);
			}
			else if (!newer) {
				accounts++; // M-SAL counts the default exp it would create
			}
			return new Data(monthLabel == null ? MonthLabel.of(today) : monthLabel,
					salaryLabel == null ? "الراتب" : salaryLabel, ledgerAmount(d.get("salary")), list, exp);
		}

		private Exp exp(JsonNode e, boolean newer) {
			List<Account> accountList = new ArrayList<>();
			Set<String> accountIds = new HashSet<>();
			Set<String> cycleIds = new HashSet<>();
			JsonNode as = e.get("accounts");
			if (as != null && as.isArray()) {
				for (JsonNode a : as) {
					if (!a.isObject()) {
						continue;
					}
					accounts++;
					String name = text(a.get("name"));
					accountList.add(new Account(uniqueId(a.get("id"), accountIds),
							name != null && !name.isBlank() ? name : DEFAULT_ACCOUNT_NAME, truthy(a.get("archived")),
							cycles(a.get("cycles"), cycleIds)));
				}
			}
			if (accountList.isEmpty()) {
				if (!newer) {
					accounts++;
				}
				accountList.add(new Account(DEFAULT_ACCOUNT_ID, DEFAULT_ACCOUNT_NAME, false, List.of()));
			}
			String lastId = text(e.get("lastAccountId"));
			String last = accountList.stream().anyMatch(a -> a.id().equals(lastId)) ? lastId
					: accountList.stream().filter(a -> !a.archived()).findFirst().orElse(accountList.getFirst()).id();

			List<Category> categoryList = new ArrayList<>();
			Set<String> categoryIds = new LinkedHashSet<>();
			JsonNode cs = e.get("categories");
			if (cs != null && cs.isArray()) {
				for (JsonNode c : cs) {
					if (!c.isObject()) {
						continue;
					}
					String name = text(c.get("name"));
					String emoji = text(c.get("emoji"));
					String id = uniqueId(c.get("id"), categoryIds);
					String code = text(c.get("code"));
					categoryList.add(new Category(id, name == null ? "" : name,
							emoji == null || emoji.isEmpty() ? DEFAULT_EMOJI : emoji, truthy(c.get("hidden")),
							code != null && DEFAULT_CODES.contains(code) ? code : DEFAULT_CODES.contains(id) ? id : null));
				}
			}
			if (categoryList.isEmpty()) {
				categoryList.addAll(DEFAULT_CATEGORIES);
				DEFAULT_CATEGORIES.forEach(c -> categoryIds.add(c.id()));
			}

			// "أخرى" in an M-SAL2.0 file has a UUID id and the code cat_other
			String other = categoryList.stream().filter(c -> OTHER_CATEGORY_ID.equals(c.code())).map(Category::id)
					.findFirst().orElse(OTHER_CATEGORY_ID);

			Set<String> accountSet = new HashSet<>();
			accountList.forEach(a -> accountSet.add(a.id()));
			List<Entry> entryList = new ArrayList<>();
			Set<String> entryIds = new HashSet<>();
			boolean needOther = false;
			JsonNode es = e.get("entries");
			if (es != null && es.isArray()) {
				for (JsonNode en : es) {
					if (!en.isObject()) {
						continue;
					}
					boolean isDeleted = truthy(en.get("deletedAt"));
					if (isDeleted) {
						deleted++;
					}
					else {
						entries++;
					}
					String id = uniqueId(en.get("id"), entryIds);
					BigDecimal amount = entryAmount(en.get("amount"));
					if (amount == null) {
						warnings.add(Code.AMOUNT_INVALID);
						continue;
					}
					String category = text(en.get("categoryId"));
					if (category == null || !categoryIds.contains(category)) {
						warnings.add(Code.CATEGORY_MISSING);
						category = other;
						needOther |= !categoryIds.contains(other);
					}
					String account = text(en.get("accountId"));
					if (account == null || !accountSet.contains(account)) {
						warnings.add(Code.ACCOUNT_MISSING);
						account = accountList.getFirst().id();
					}
					Instant createdAt = instant(en.get("createdAt"));
					LocalDate date = date(en.get("date"));
					if (date == null) {
						warnings.add(Code.DATE_INVALID);
						date = createdAt != null ? LocalDate.ofInstant(createdAt, zone) : today;
					}
					Instant deletedAt = null;
					if (isDeleted) {
						deletedAt = instant(en.get("deletedAt"));
						if (deletedAt == null) {
							deletedAt = now;
						}
					}
					String name = text(en.get("name"));
					entryList.add(new Entry(id, "income".equals(text(en.get("type"))), amount, name == null ? "" : name,
							category, date, account, createdAt, instant(en.get("updatedAt")), deletedAt));
				}
			}
			if (needOther) {
				categoryList.add(cat(OTHER_CATEGORY_ID, "أخرى", DEFAULT_EMOJI));
			}
			return new Exp(accountList, last, categoryList, entryList);
		}

		/** Cycles with a valid start date, one per date (salary_cycles' unique key, R-CYC-03). */
		private List<Cycle> cycles(JsonNode cs, Set<String> ids) {
			List<Cycle> list = new ArrayList<>();
			if (cs == null || !cs.isArray()) {
				return list;
			}
			Set<LocalDate> starts = new HashSet<>();
			for (JsonNode c : cs) {
				if (!c.isObject()) {
					continue;
				}
				cycles++;
				String id = uniqueId(c.get("id"), ids);
				LocalDate start = date(c.get("startDate"));
				if (start == null || !starts.add(start)) {
					warnings.add(Code.CYCLE_INVALID);
					continue;
				}
				BigDecimal allowance = number(c.get("allowance"));
				if (allowance == null || allowance.signum() < 0 || allowance.compareTo(MAX_ENTRY) > 0) {
					allowance = Money.ZERO;
				}
				LocalDate next = date(c.get("expectedNextSalaryDate"));
				list.add(new Cycle(id, start, allowance, next != null && next.isAfter(start) ? next : null));
			}
			return list;
		}

		private Amount ledgerAmount(JsonNode node) {
			String raw = node == null ? null : node.isString() ? node.asString() : node.isNumber() ? node.decimalValue().toPlainString() : null;
			Amount amount = LedgerAmounts.parse(raw);
			if (amount == null) {
				warnings.add(Code.AMOUNT_TOO_LARGE);
				return LedgerAmounts.EMPTY;
			}
			return amount;
		}

		/** A file id when it is a non-empty string not used before in its list, else a generated one. */
		private String uniqueId(JsonNode node, Set<String> used) {
			String id = text(node);
			if (id == null || id.isEmpty() || id.startsWith(ImportFile.GENERATED) || !used.add(id)) {
				id = ImportFile.GENERATED + (++generated);
				used.add(id);
			}
			return id;
		}

		private static BigDecimal entryAmount(JsonNode node) {
			BigDecimal value = number(node);
			if (value == null) {
				return null;
			}
			value = Money.round(value);
			return value.signum() > 0 && value.compareTo(MAX_ENTRY) <= 0 ? value : null;
		}

		private static BigDecimal number(JsonNode node) {
			if (node == null) {
				return null;
			}
			if (node.isNumber()) {
				return node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue()) ? null : node.decimalValue();
			}
			if (node.isString()) {
				try {
					return new BigDecimal(LedgerAmounts.latinDigits(node.asString()).strip());
				}
				catch (NumberFormatException e) {
					return null;
				}
			}
			return null;
		}

		private static LocalDate date(JsonNode node) {
			String s = text(node);
			if (s == null || !DATE.matcher(s).matches()) {
				return null;
			}
			try {
				return LocalDate.parse(s);
			}
			catch (DateTimeParseException e) {
				return null;
			}
		}

		/** Milliseconds since 1970 as M-SAL stores them, or an ISO instant. */
		private static Instant instant(JsonNode node) {
			if (node == null) {
				return null;
			}
			if (node.isIntegralNumber() && node.canConvertToLong()) {
				long ms = node.longValue();
				return Math.abs(ms) <= 8_640_000_000_000_000L ? Instant.ofEpochMilli(ms) : null;
			}
			if (node.isString()) {
				try {
					return Instant.parse(node.asString());
				}
				catch (DateTimeParseException e) {
					return null;
				}
			}
			return null;
		}
	}

	private static boolean hasObject(JsonNode array) {
		for (JsonNode n : array) {
			if (n.isObject()) {
				return true;
			}
		}
		return false;
	}

	private static String text(JsonNode node) {
		return node != null && node.isString() ? node.asString() : null;
	}

	/** JavaScript's {@code !!value}. */
	static boolean truthy(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return false;
		}
		if (node.isBoolean()) {
			return node.booleanValue();
		}
		if (node.isNumber()) {
			double d = node.doubleValue();
			return d != 0 && !Double.isNaN(d);
		}
		if (node.isString()) {
			return !node.asString().isEmpty();
		}
		return true;
	}

	private static Category cat(String id, String name, String emoji) {
		return new Category(id, name, emoji, false, id);
	}
}
