package com.abdullah.msal2.expenses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.abdullah.msal2.system.money.Money;

/**
 * The entries list grouped by day (R-ENT-10), from M-SAL's {@code groupByDay}: newest day first,
 * and inside a day the newest created first. A day's total counts expenses only; income is
 * shown separately.
 */
public final class EntryDays {

	/** Newest date, then newest created, then id (descending), so the order never shuffles. */
	static final Comparator<EntryData> NEWEST_FIRST = Comparator.comparing(EntryData::date)
			.thenComparing(EntryData::createdAt)
			.thenComparing(e -> e.id().toString())
			.reversed();

	private EntryDays() {
	}

	public record Day(LocalDate date, List<EntryData> entries, BigDecimal spent, BigDecimal extra) {
	}

	public static List<Day> group(List<EntryData> entries) {
		List<Day> days = new ArrayList<>();
		LocalDate date = null;
		List<EntryData> current = null;
		for (EntryData entry : entries.stream().sorted(NEWEST_FIRST).toList()) {
			if (!entry.date().equals(date)) {
				if (current != null) {
					days.add(day(date, current));
				}
				date = entry.date();
				current = new ArrayList<>();
			}
			current.add(entry);
		}
		if (current != null) {
			days.add(day(date, current));
		}
		return days;
	}

	private static Day day(LocalDate date, List<EntryData> entries) {
		BigDecimal spent = Money.sum(entries.stream().filter(EntryData::isExpense).map(EntryData::amount).toList());
		BigDecimal extra = Money.sum(entries.stream().filter(EntryData::isIncome).map(EntryData::amount).toList());
		return new Day(date, List.copyOf(entries), spent, extra);
	}
}
