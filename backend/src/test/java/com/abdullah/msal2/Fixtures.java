package com.abdullah.msal2;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.abdullah.msal2.cycles.CycleData;
import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.expenses.EntryType;

/**
 * Short builders for the calculation tests, so they read like M-SAL's JS tests:
 * {@code cyc("a", "2026-01-27")} and {@code exp("e1", "2026-02-20", "10")}.
 * Names become stable UUIDs; assertions compare them with {@link #ids(String...)}.
 */
public final class Fixtures {

	private static final String PREFIX = "msal-test:";

	private Fixtures() {
	}

	public static UUID id(String name) {
		return UUID.nameUUIDFromBytes((PREFIX + name).getBytes(StandardCharsets.UTF_8));
	}

	public static List<UUID> ids(String... names) {
		return Arrays.stream(names).map(Fixtures::id).toList();
	}

	public static List<UUID> idsOf(List<EntryData> entries) {
		return entries.stream().map(EntryData::id).toList();
	}

	public static LocalDate d(String date) {
		return LocalDate.parse(date);
	}

	public static BigDecimal m(String amount) {
		return new BigDecimal(amount);
	}

	public static CycleData cyc(String name, String startDate) {
		return cyc(name, startDate, "1000", null);
	}

	public static CycleData cyc(String name, String startDate, String allowance) {
		return cyc(name, startDate, allowance, null);
	}

	public static CycleData cyc(String name, String startDate, String allowance, String expectedNextSalaryDate) {
		return new CycleData(id(name), d(startDate), m(allowance),
				expectedNextSalaryDate == null ? null : d(expectedNextSalaryDate));
	}

	public static EntryData exp(String name, String date, String amount) {
		return new EntryData(id(name), EntryType.EXPENSE, m(amount), "", id("cat_food"), d(date), id("acc_main"),
				Instant.EPOCH.plusMillis(1), null);
	}

	public static EntryData income(String name, String date, String amount) {
		return withType(exp(name, date, amount), EntryType.INCOME);
	}

	public static EntryData withType(EntryData e, EntryType type) {
		return new EntryData(e.id(), type, e.amount(), e.name(), e.categoryId(), e.date(), e.accountId(),
				e.createdAt(), e.deletedAt());
	}

	public static EntryData deleted(EntryData e) {
		return new EntryData(e.id(), e.type(), e.amount(), e.name(), e.categoryId(), e.date(), e.accountId(),
				e.createdAt(), Instant.EPOCH.plusMillis(9));
	}

	public static EntryData inCategory(EntryData e, String category) {
		return new EntryData(e.id(), e.type(), e.amount(), e.name(), id(category), e.date(), e.accountId(),
				e.createdAt(), e.deletedAt());
	}

	public static EntryData named(EntryData e, String entryName) {
		return new EntryData(e.id(), e.type(), e.amount(), entryName, e.categoryId(), e.date(), e.accountId(),
				e.createdAt(), e.deletedAt());
	}

	public static EntryData createdAt(EntryData e, long millis) {
		return new EntryData(e.id(), e.type(), e.amount(), e.name(), e.categoryId(), e.date(), e.accountId(),
				Instant.ofEpochMilli(millis), e.deletedAt());
	}
}
