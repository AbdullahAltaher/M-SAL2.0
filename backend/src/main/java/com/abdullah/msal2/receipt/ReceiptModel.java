package com.abdullah.msal2.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.abdullah.msal2.cycles.CycleData;
import com.abdullah.msal2.cycles.CycleSummary;
import com.abdullah.msal2.cycles.Cycles;
import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.expenses.EntryDays;
import com.abdullah.msal2.expenses.EntryRules;
import com.abdullah.msal2.expenses.EntryType;
import com.abdullah.msal2.stats.CategoryTotals;
import com.abdullah.msal2.system.money.Money;

/**
 * The data of the expenses receipt for one cycle, or for "no cycle", of one account
 * (R-RCP-01), from M-SAL's {@code buildReceiptModel}. Angular draws the picture; day labels
 * ("اليوم", "أمس") are written there from {@code date}.
 *
 * @param start     null for "no cycle"
 * @param end       the cycle's last day, or today for the open cycle; null for "no cycle"
 * @param remaining allowance + extra − spent; for "no cycle" (no allowance) extra − spent
 */
public record ReceiptModel(
		String accountName,
		LocalDate start,
		LocalDate end,
		BigDecimal allowance,
		BigDecimal extra,
		BigDecimal spent,
		BigDecimal remaining,
		List<Category> categories,
		List<Day> days) {

	public record Category(String name, BigDecimal total, int pct) {
	}

	public record Day(LocalDate date, BigDecimal spent, BigDecimal extra, List<Line> entries) {
	}

	public record Line(String name, EntryType type, BigDecimal amount) {
	}

	/**
	 * @param entries      the account's entries
	 * @param cycleId      the cycle, or null for "no cycle"
	 * @param categoryName a category's display name ("أخرى" when missing)
	 */
	public static ReceiptModel build(String accountName, List<CycleData> cycles, List<EntryData> entries, UUID cycleId,
			LocalDate today, Function<UUID, String> categoryName) {
		Cycles.Buckets buckets = Cycles.bucket(cycles, entries);
		List<EntryData> list;
		LocalDate start = null;
		LocalDate end = null;
		BigDecimal allowance = Money.ZERO;
		CycleSummary summary = null;
		if (cycleId == null) {
			list = buckets.noCycle();
		}
		else {
			Optional<CycleData> cycle = cycles.stream().filter(c -> c.id().equals(cycleId)).findFirst();
			list = cycle.map(c -> buckets.byCycle().get(c.id())).orElse(List.of());
			if (cycle.isPresent()) {
				summary = CycleSummary.of(cycles, cycle.get(), entries, today);
				start = summary.start();
				end = summary.lastDay() != null ? summary.lastDay() : today;
				allowance = summary.allowance();
			}
		}
		List<EntryData> live = list.stream().filter(EntryData::isLive).toList();

		List<Day> days = EntryDays.group(live).stream()
				.map(d -> new Day(d.date(), d.spent(), d.extra(), d.entries().stream()
						.map(e -> new Line(EntryRules.displayName(e, categoryName), e.type(), e.amount()))
						.toList()))
				.toList();
		BigDecimal spent = Money.sum(live.stream().filter(EntryData::isExpense).map(EntryData::amount).toList());
		BigDecimal extra = Money.sum(live.stream().filter(EntryData::isIncome).map(EntryData::amount).toList());
		BigDecimal remaining = summary != null ? summary.remaining() : Money.round(extra.subtract(spent));
		List<Category> categories = CategoryTotals.of(live).stream()
				.map(t -> new Category(categoryName.apply(t.categoryId()), t.total(), t.pct()))
				.toList();
		return new ReceiptModel(accountName, start, end, allowance, extra, spent, remaining, categories, days);
	}
}
