package com.abdullah.msal2.cycles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Predicate;

import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.system.money.Money;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * The figures of one cycle (R-CYC-05) and "how much can I spend today" (R-CYC-04), from
 * M-SAL's {@code cycleSummary}. Soft-deleted entries are ignored.
 * <p>
 * Fields that only exist for an open cycle with an expected salary date ({@code daysLeft},
 * {@code startOfToday}, {@code todayBudget}, {@code canSpendToday}) are null otherwise.
 *
 * @param progress      spent ÷ budget, at most 1 (a ratio, 4 decimals)
 * @param todayBudget   fixed for the whole day: start-of-day balance ÷ days left, plus today's income
 * @param canSpendToday today's budget − spent today; negative when over
 * @param overToday     how much today is over, else 0
 * @param tomorrow      when over today: what is left for each remaining day, else null
 */
public record CycleSummary(
		LocalDate start,
		LocalDate endExclusive,
		LocalDate lastDay,
		boolean open,
		BigDecimal allowance,
		BigDecimal extra,
		BigDecimal budget,
		BigDecimal spent,
		BigDecimal remaining,
		BigDecimal spentToday,
		@JsonFormat(shape = JsonFormat.Shape.NUMBER) BigDecimal progress,
		long daysElapsed,
		Long totalDays,
		Long daysLeft,
		BigDecimal startOfToday,
		BigDecimal todayBudget,
		BigDecimal canSpendToday,
		BigDecimal overToday,
		BigDecimal tomorrow,
		BigDecimal dailyAverage,
		boolean over,
		int entryCount) {

	public static CycleSummary of(List<CycleData> cycles, CycleData cycle, List<EntryData> entries, LocalDate today) {
		Cycles.Bounds bounds = Cycles.bounds(cycles, cycle);
		List<EntryData> mine = Cycles.bucket(cycles, entries).byCycle().getOrDefault(cycle.id(), List.of()).stream()
				.filter(EntryData::isLive)
				.toList();

		BigDecimal spent = sum(mine, e -> e.isExpense());
		BigDecimal extra = sum(mine, e -> e.isIncome());
		BigDecimal spentToday = sum(mine, e -> e.isExpense() && e.date().equals(today));
		BigDecimal allowance = cleanAllowance(cycle.allowance());
		BigDecimal budget = Money.round(allowance.add(extra));
		BigDecimal remaining = Money.round(budget.subtract(spent));

		LocalDate lastCounted = bounds.lastDay() != null && bounds.lastDay().isBefore(today) ? bounds.lastDay() : today;
		long daysElapsed = lastCounted.isBefore(bounds.start()) ? 0 : days(bounds.start(), lastCounted) + 1;

		Long totalDays = null;
		Long daysLeft = null;
		BigDecimal startOfToday = null;
		BigDecimal todayBudget = null;
		BigDecimal leftToday = null;
		BigDecimal overToday = Money.ZERO;
		BigDecimal tomorrow = null;
		if (!bounds.open()) {
			totalDays = days(bounds.start(), bounds.endExclusive());
		}
		else {
			LocalDate expected = cycle.expectedNextSalaryDate();
			if (expected != null && expected.isAfter(bounds.start())) {
				totalDays = days(bounds.start(), expected);
				long left = Math.max(1, days(today, expected));
				daysLeft = left;
				// Fixed for the whole day: only entries before today, plus income that arrived today
				BigDecimal incomeToday = sum(mine, e -> e.isIncome() && e.date().equals(today));
				BigDecimal incomeBefore = sum(mine, e -> e.isIncome() && e.date().isBefore(today));
				BigDecimal spentBefore = sum(mine, e -> e.isExpense() && e.date().isBefore(today));
				startOfToday = Money.round(allowance.add(incomeBefore).subtract(spentBefore));
				todayBudget = Money.round(Money.divideExact(startOfToday, left).add(incomeToday));
				leftToday = Money.round(todayBudget.subtract(spentToday));
				if (leftToday.signum() < 0) {
					overToday = Money.round(leftToday.negate());
					if (left > 1) {
						tomorrow = Money.divide(startOfToday.add(incomeToday).subtract(spentToday), left - 1);
					}
				}
			}
		}

		return new CycleSummary(bounds.start(), bounds.endExclusive(), bounds.lastDay(), bounds.open(), allowance,
				extra, budget, spent, remaining, spentToday, progress(spent, budget), daysElapsed, totalDays, daysLeft,
				startOfToday, todayBudget, leftToday, overToday, tomorrow,
				Money.divide(spent, Math.max(1, daysElapsed)), remaining.signum() < 0, mine.size());
	}

	/** An allowance is never negative ({@code cleanAllowance}). */
	private static BigDecimal cleanAllowance(BigDecimal allowance) {
		return allowance == null || allowance.signum() < 0 ? Money.ZERO : Money.round(allowance);
	}

	private static BigDecimal progress(BigDecimal spent, BigDecimal budget) {
		if (budget.signum() > 0) {
			BigDecimal ratio = spent.divide(budget, 4, RoundingMode.HALF_UP);
			return ratio.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE.setScale(4) : ratio;
		}
		return (spent.signum() > 0 ? BigDecimal.ONE : BigDecimal.ZERO).setScale(4);
	}

	private static long days(LocalDate from, LocalDate to) {
		return ChronoUnit.DAYS.between(from, to);
	}

	private static BigDecimal sum(List<EntryData> entries, Predicate<EntryData> filter) {
		return Money.sum(entries.stream().filter(filter).map(EntryData::amount).toList());
	}
}
