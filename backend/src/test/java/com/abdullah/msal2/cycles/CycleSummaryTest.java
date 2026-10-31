package com.abdullah.msal2.cycles;

import static com.abdullah.msal2.Fixtures.cyc;
import static com.abdullah.msal2.Fixtures.d;
import static com.abdullah.msal2.Fixtures.deleted;
import static com.abdullah.msal2.Fixtures.exp;
import static com.abdullah.msal2.Fixtures.income;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.expenses.EntryData;

/**
 * R-CYC-04 ("can spend today") and R-CYC-05 (cycle summary), ported from M-SAL's
 * test/cycles.test.js with the same numbers, plus the examples in BUSINESS_RULES.md.
 */
class CycleSummaryTest {

	private static CycleSummary summary(CycleData cycle, String today, EntryData... entries) {
		return CycleSummary.of(List.of(cycle), cycle, List.of(entries), d(today));
	}

	private static void money(BigDecimal actual, String expected) {
		assertThat(actual).isEqualByComparingTo(expected);
	}

	// ── R-CYC-04: the business rules example ────────────────────

	@Test
	void businessRulesExampleUnderBudget() {
		CycleData cycle = cyc("a", "2026-11-01", "3000", "2026-12-01");
		CycleSummary s = summary(cycle, "2026-11-11", exp("before", "2026-11-05", "1000"), exp("t", "2026-11-11", "30"));
		money(s.startOfToday(), "2000");
		assertThat(s.daysLeft()).isEqualTo(20);
		money(s.todayBudget(), "100.00");
		money(s.canSpendToday(), "70.00");
		assertThat(s.tomorrow()).isNull();
	}

	@Test
	void businessRulesExampleOverBudget() {
		CycleData cycle = cyc("a", "2026-11-01", "3000", "2026-12-01");
		CycleSummary s = summary(cycle, "2026-11-11", exp("before", "2026-11-05", "1000"), exp("t", "2026-11-11", "150"));
		money(s.canSpendToday(), "-50.00");
		money(s.overToday(), "50.00");
		money(s.tomorrow(), "97.37"); // (2000 − 150) ÷ 19
	}

	// ── R-CYC-05: summary figures ───────────────────────────────

	@Test
	void remainingIsAllowancePlusIncomeMinusExpensesAndDeletedEntriesAreIgnored() {
		CycleData cycle = cyc("a", "2026-05-01", "1000", "2026-05-31");
		CycleSummary s = summary(cycle, "2026-05-10", exp("1", "2026-05-02", "100.10"), exp("2", "2026-05-02", "200.20"),
				exp("3", "2026-05-03", "0.30"), income("inc", "2026-05-04", "50"),
				deleted(exp("del", "2026-05-04", "999")));
		money(s.spent(), "300.60");
		money(s.extra(), "50");
		money(s.remaining(), "749.40");
		assertThat(s.entryCount()).isEqualTo(4);
		assertThat(s.over()).isFalse();
	}

	@Test
	void spentTodayDaysAndCanSpendToday() {
		CycleData cycle = cyc("a", "2026-05-01", "1000", "2026-05-31");
		CycleSummary s = summary(cycle, "2026-05-10", exp("1", "2026-05-10", "40"), exp("2", "2026-05-10", "6.25"),
				exp("3", "2026-05-09", "100"));
		money(s.spentToday(), "46.25");
		assertThat(s.daysElapsed()).isEqualTo(10);
		assertThat(s.totalDays()).isEqualTo(30);
		assertThat(s.daysLeft()).isEqualTo(21);
		money(s.remaining(), "853.75");
		// Fixed at the start of the day: (1000 − 100 spent before today) ÷ 21 days left
		money(s.startOfToday(), "900");
		money(s.todayBudget(), "42.86");
		money(s.canSpendToday(), "-3.39");
		money(s.dailyAverage(), "14.63");
	}

	@Test
	void theExactReportedCase() {
		// Remaining 2,617.98, 25 days left, 441.28 spent today
		CycleData cycle = cyc("a", "2026-09-27", "3500", "2026-10-28");
		CycleSummary s = summary(cycle, "2026-10-03", exp("before", "2026-09-30", "440.74"),
				exp("today", "2026-10-03", "441.28"));
		money(s.remaining(), "2617.98");
		assertThat(s.daysLeft()).isEqualTo(25);
		money(s.startOfToday(), "3059.26");
		money(s.todayBudget(), "122.37");
		money(s.spentToday(), "441.28");
		money(s.canSpendToday(), "-318.91");
		money(s.overToday(), "318.91");
		money(s.tomorrow(), "109.08"); // 2617.98 ÷ (25 − 1)
	}

	@Test
	void todayBudgetStaysFixedWhileYouSpend() {
		CycleData cycle = cyc("a", "2026-09-27", "3500", "2026-10-28");
		EntryData before = exp("before", "2026-09-30", "440.74");
		List<BigDecimal> budgets = new ArrayList<>();
		for (String amount : List.of("0", "10", "100", "441.28", "900")) {
			CycleSummary s = amount.equals("0") ? summary(cycle, "2026-10-03", before)
					: summary(cycle, "2026-10-03", before, exp("t", "2026-10-03", amount));
			budgets.add(s.todayBudget());
		}
		assertThat(budgets).allSatisfy(b -> money(b, "122.37"));
		money(summary(cycle, "2026-10-03", before, exp("t", "2026-10-03", "100")).canSpendToday(), "22.37");

		CycleSummary exact = summary(cycle, "2026-10-03", before, exp("t", "2026-10-03", "122.37"));
		money(exact.canSpendToday(), "0");
		money(exact.overToday(), "0");
		assertThat(exact.tomorrow()).as("not over: no 'tomorrow' line").isNull();
		assertThat(summary(cycle, "2026-10-03", before, exp("t", "2026-10-03", "122.38")).overToday())
				.isPositive();
	}

	@Test
	void nothingSpentTodayLeavesTheWholeShareAndNoTomorrow() {
		CycleData cycle = cyc("a", "2026-09-27", "3000", "2026-10-27");
		CycleSummary s = summary(cycle, "2026-10-03", exp("e", "2026-09-30", "300"));
		assertThat(s.daysLeft()).isEqualTo(24);
		money(s.startOfToday(), "2700");
		money(s.todayBudget(), "112.50");
		money(s.spentToday(), "0");
		money(s.canSpendToday(), "112.50");
		money(s.overToday(), "0");
		assertThat(s.tomorrow()).isNull();
	}

	@Test
	void incomeDatedTodayAddsToTodaysBudgetNotTheStartOfDayPool() {
		CycleData cycle = cyc("a", "2026-09-27", "3000", "2026-10-27");
		CycleSummary s = summary(cycle, "2026-10-03", exp("e", "2026-09-30", "300"), income("i", "2026-10-03", "50"),
				exp("t", "2026-10-03", "60"));
		money(s.startOfToday(), "2700");
		money(s.todayBudget(), "162.50"); // 2700 ÷ 24 + 50
		money(s.canSpendToday(), "102.50");

		// Income from an earlier day is part of the start-of-day pool
		CycleSummary earlier = summary(cycle, "2026-10-03", income("i", "2026-10-01", "240"));
		money(earlier.startOfToday(), "3240");
		money(earlier.todayBudget(), "135");

		// The tomorrow figure counts today's income and spending: (3000 + 50 − 400) ÷ 23
		CycleSummary over = summary(cycle, "2026-10-03", income("i", "2026-10-03", "50"), exp("t", "2026-10-03", "400"));
		money(over.tomorrow(), "115.22");
	}

	@Test
	void deletedAndFutureEntriesDoNotCountAndTheLastDayHasNoTomorrow() {
		CycleData cycle = cyc("a", "2026-09-27", "3000", "2026-10-27");
		CycleSummary s = summary(cycle, "2026-10-03", deleted(exp("gone", "2026-09-30", "999")),
				exp("future", "2026-10-10", "500"), deleted(exp("t", "2026-10-03", "5")));
		money(s.startOfToday(), "3000");
		money(s.spentToday(), "0");
		money(s.canSpendToday(), "125");

		CycleSummary last = summary(cyc("a", "2026-09-27", "100", "2026-10-04"), "2026-10-03",
				exp("t", "2026-10-03", "150"));
		assertThat(last.daysLeft()).isEqualTo(1);
		money(last.canSpendToday(), "-50");
		assertThat(last.tomorrow()).as("one day left: no tomorrow in this cycle").isNull();
	}

	@Test
	void daysLeftIsAtLeastOneOnOrAfterTheExpectedSalaryDate() {
		CycleData cycle = cyc("a", "2026-05-01", "300", "2026-05-31");
		for (String today : List.of("2026-05-30", "2026-05-31", "2026-06-05")) {
			CycleSummary s = summary(cycle, today);
			assertThat(s.daysLeft()).as(today).isEqualTo(1);
			money(s.canSpendToday(), "300");
		}
	}

	@Test
	void noOrEarlierExpectedSalaryDateMeansNoDailyFigures() {
		for (String expected : new String[] { null, "2026-04-01", "2026-05-01" }) {
			CycleSummary s = summary(cyc("a", "2026-05-01", "300", expected), "2026-05-10");
			assertThat(s.daysLeft()).as(String.valueOf(expected)).isNull();
			assertThat(s.canSpendToday()).as(String.valueOf(expected)).isNull();
			assertThat(s.todayBudget()).isNull();
		}
	}

	@Test
	void overBudgetIsFlaggedAndTheDailyFigureGoesNegative() {
		CycleSummary s = summary(cyc("a", "2026-05-01", "100", "2026-05-11"), "2026-05-06",
				exp("1", "2026-05-02", "150"));
		money(s.remaining(), "-50");
		assertThat(s.over()).isTrue();
		assertThat(s.daysLeft()).isEqualTo(5);
		money(s.canSpendToday(), "-10");
		money(s.progress(), "1");
	}

	@Test
	void aClosedCycleUsesItsOwnLengthNotToday() {
		List<CycleData> cycles = List.of(cyc("a", "2026-01-27", "500", "2026-02-20"), cyc("b", "2026-02-27"));
		CycleSummary s = CycleSummary.of(cycles, cycles.get(0), List.of(exp("1", "2026-02-01", "100")),
				d("2026-04-01"));
		assertThat(s.open()).isFalse();
		assertThat(s.totalDays()).isEqualTo(31);
		assertThat(s.daysElapsed()).isEqualTo(31);
		assertThat(s.daysLeft()).isNull();
		assertThat(s.canSpendToday()).isNull();
		money(s.spentToday(), "0");
	}

	@Test
	void aCycleStartingInTheFutureHasZeroDaysElapsed() {
		CycleSummary s = summary(cyc("a", "2026-06-01", "100"), "2026-05-20");
		assertThat(s.daysElapsed()).isZero();
		money(s.dailyAverage(), "0");
	}

	@Test
	void anEntryOnTheBoundaryCountsInTheNewCycleOnly() {
		List<CycleData> cycles = List.of(cyc("a", "2026-01-27", "1000"), cyc("b", "2026-02-27", "1000"));
		List<EntryData> entries = List.of(exp("1", "2026-02-26", "10"), exp("2", "2026-02-27", "20"));
		money(CycleSummary.of(cycles, cycles.get(0), entries, d("2026-03-01")).spent(), "10");
		money(CycleSummary.of(cycles, cycles.get(1), entries, d("2026-03-01")).spent(), "20");
	}

	@Test
	void progressIsCappedAtOneAndHandlesAZeroBudget() {
		money(summary(cyc("a", "2026-05-01", "1000"), "2026-05-10", exp("1", "2026-05-02", "250")).progress(), "0.25");
		money(summary(cyc("a", "2026-05-01", "0"), "2026-05-10", exp("1", "2026-05-02", "5")).progress(), "1");
		money(summary(cyc("a", "2026-05-01", "0"), "2026-05-10").progress(), "0");
	}
}
