package com.abdullah.msal2.importer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * An import file after M-SAL's sanitizeState (R-IO-02, R-IO-04): every known field has the
 * right type, junk is skipped, missing ids are generated. Unknown fields are not here; the whole
 * original file is kept in {@code imports.raw_data} (DATABASE.md D-09).
 *
 * @param newer the file comes from a newer schema version
 */
public record ImportFile(Kind kind, boolean newer, String currentId, List<Profile> profiles, Warnings warnings) {

	/** STATE: a full export ({@code profiles[]}). SINGLE: an old one-person file ({@code blocks[]} at the root). */
	public enum Kind {
		STATE, SINGLE
	}

	/** One "person", which becomes a budget. The id is null for a SINGLE file. */
	public record Profile(String id, String name, Data data) {
	}

	/**
	 * @param exp null when the profile has no Expenses part (an older file): its expenses are left
	 *            alone (R-IO-03)
	 */
	public record Data(String monthLabel, String salaryLabel, Amount salary, List<Block> blocks, Exp exp) {
	}

	public record Block(String id, String title, List<Row> rows) {
	}

	public record Row(String id, String name, Amount amount, boolean done) {
	}

	/** A monthly budget amount: {@code value} is null for an empty field (R-BUD-03). */
	public record Amount(BigDecimal value, boolean approx) {
	}

	public record Exp(List<Account> accounts, String lastAccountId, List<Category> categories, List<Entry> entries) {
	}

	public record Account(String id, String name, boolean archived, List<Cycle> cycles) {
	}

	/** {@code expectedNextSalaryDate} is null when absent or not after the start. */
	public record Cycle(String id, LocalDate startDate, BigDecimal allowance, LocalDate expectedNextSalaryDate) {
	}

	/**
	 * @param code the default category it is ({@code cat_food}): M-SAL's id, or M-SAL2.0's
	 *             {@code code} field; null for the user's own categories
	 */
	public record Category(String id, String name, String emoji, boolean hidden, String code) {
	}

	/**
	 * An entry ready to save: its account and category are ids of this profile's file data
	 * (fixed by DATABASE.md 6.2), and {@code categoryId} may be "cat_other" to create.
	 */
	public record Entry(String id, boolean income, BigDecimal amount, String name, String categoryId, LocalDate date,
			String accountId, Instant createdAt, Instant updatedAt, Instant deletedAt) {
	}

	/** Generated ids (for records without one) start with this, and are not kept as legacy ids. */
	public static final String GENERATED = "gen-";
}
