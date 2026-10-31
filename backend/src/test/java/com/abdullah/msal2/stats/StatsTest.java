package com.abdullah.msal2.stats;

import static com.abdullah.msal2.Fixtures.cyc;
import static com.abdullah.msal2.Fixtures.d;
import static com.abdullah.msal2.Fixtures.deleted;
import static com.abdullah.msal2.Fixtures.exp;
import static com.abdullah.msal2.Fixtures.id;
import static com.abdullah.msal2.Fixtures.ids;
import static com.abdullah.msal2.Fixtures.inCategory;
import static com.abdullah.msal2.Fixtures.income;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.cycles.CycleData;
import com.abdullah.msal2.cycles.CycleSummary;
import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.system.money.Money;

/**
 * R-STA-01 to R-STA-05, ported from M-SAL's test/expenses.test.js, plus the examples in
 * BUSINESS_RULES.md.
 */
class StatsTest {

	private static EntryData in(String category, String name, String date, String amount) {
		return inCategory(exp(name, date, amount), category);
	}

	// ── R-STA-02: totals per category ───────────────────────────

	@Test
	void biggestFirstAndPercentagesAddUpToExactly100() {
		List<EntryData> list = List.of(in("a", "1", "2026-10-02", "100"), in("b", "2", "2026-10-02", "100"),
				in("c", "3", "2026-10-02", "100"), inCategory(income("i", "2026-10-02", "999"), "a"),
				deleted(in("a", "d", "2026-10-02", "999")));
		List<CategoryTotals.CategoryTotal> totals = CategoryTotals.of(list);
		assertThat(totals).hasSize(3);
		assertThat(totals).extracting(CategoryTotals.CategoryTotal::pct).containsExactlyInAnyOrder(34, 33, 33);
		assertThat(totals.getFirst().total()).as("income and deleted entries are excluded").isEqualByComparingTo("100");
		// Business rules example: 33.33% each, and the missing point goes to the first row
		assertThat(totals.getFirst().pct()).isEqualTo(34);

		List<CategoryTotals.CategoryTotal> t2 = CategoryTotals.of(List.of(in("a", "1", "2026-10-02", "70"),
				in("b", "2", "2026-10-02", "20"), in("c", "3", "2026-10-02", "10")));
		assertThat(t2).extracting(CategoryTotals.CategoryTotal::categoryId, CategoryTotals.CategoryTotal::pct)
				.containsExactly(tuple(id("a"), 70), tuple(id("b"), 20), tuple(id("c"), 10));
		assertThat(CategoryTotals.of(List.of())).isEmpty();
	}

	@Test
	void awkwardCentsStillAddUp() {
		List<EntryData> list = new ArrayList<>();
		String[] amounts = { "0.1", "0.2", "0.3", "0.7" };
		for (int i = 0; i < amounts.length; i++) {
			list.add(in("k" + i, "e" + i, "2026-10-02", amounts[i]));
		}
		List<CategoryTotals.CategoryTotal> totals = CategoryTotals.of(list);
		assertThat(totals.stream().mapToInt(CategoryTotals.CategoryTotal::pct).sum()).isEqualTo(100);
		assertThat(Money.sum(totals.stream().map(CategoryTotals.CategoryTotal::total).toList()))
				.isEqualByComparingTo("1.30");
	}

	// ── R-STA-03: last 7 days ───────────────────────────────────

	@Test
	void lastSevenDaysOneBarPerDayNeverBeforeTheCycleStartExpensesOnly() {
		List<EntryData> list = List.of(exp("1", "2026-10-02", "10"), exp("2", "2026-10-02", "5"),
				exp("3", "2026-09-30", "7"), income("i", "2026-10-01", "50"));
		List<Stats.DayTotal> days = Stats.lastDays(list, d("2026-09-01"), d("2026-10-02"), 7);
		assertThat(days).hasSize(7);
		assertThat(days.get(0).date()).isEqualTo(d("2026-09-26"));
		assertThat(days.get(6).date()).isEqualTo(d("2026-10-02"));
		assertThat(days.get(6).total()).isEqualByComparingTo("15");
		assertThat(days.get(4).total()).isEqualByComparingTo("7");
		assertThat(days.get(5).total()).isEqualByComparingTo("0");

		assertThat(Stats.lastDays(list, d("2026-09-30"), d("2026-10-02"), 7)).extracting(Stats.DayTotal::date)
				.containsExactly(d("2026-09-30"), d("2026-10-01"), d("2026-10-02"));
	}

	// ── R-STA-04: trend ─────────────────────────────────────────

	@Test
	void trendOfTheLastSixCyclesEndingAtTheSelectedOneOldestFirst() {
		List<CycleData> cycles = new ArrayList<>();
		List<EntryData> list = new ArrayList<>();
		for (int i = 1; i <= 8; i++) {
			cycles.add(cyc("c" + i, "2026-0" + i + "-10"));
			list.add(exp("e" + i, "2026-0" + i + "-15", String.valueOf(i * 100)));
		}
		List<Stats.CycleTotal> trend = Stats.trend(cycles, list, id("c8"), 6);
		assertThat(trend).extracting(Stats.CycleTotal::cycleId)
				.containsExactlyElementsOf(ids("c3", "c4", "c5", "c6", "c7", "c8"));
		assertThat(trend).extracting(t -> t.total().intValue()).containsExactly(300, 400, 500, 600, 700, 800);
		assertThat(Stats.trend(cycles, list, id("c2"), 6)).extracting(Stats.CycleTotal::cycleId)
				.containsExactlyElementsOf(ids("c1", "c2"));
		assertThat(Stats.trend(cycles, list, id("nope"), 6)).isEmpty();
	}

	// ── R-STA-05: changes against the previous cycle ────────────

	@Test
	void changesAreNewUpOrDownBiggestFirstAndNoneForTheFirstCycle() {
		List<CycleData> cycles = List.of(cyc("a", "2026-08-01"), cyc("b", "2026-09-01"));
		List<EntryData> list = List.of(in("food", "1", "2026-08-05", "100"), in("fuel", "2", "2026-08-06", "50"),
				in("gone", "3", "2026-08-07", "30"), in("food", "4", "2026-09-05", "160"),
				in("fuel", "5", "2026-09-06", "50"), in("coffee", "6", "2026-09-07", "80"));
		assertThat(Stats.changes(cycles, list, id("b")))
				.extracting(Stats.CategoryChange::categoryId, Stats.CategoryChange::kind, c -> c.delta().intValue())
				.containsExactly(tuple(id("coffee"), Stats.ChangeKind.NEW, 80), tuple(id("food"), Stats.ChangeKind.UP, 60),
						tuple(id("gone"), Stats.ChangeKind.DOWN, -30));
		assertThat(Stats.changes(cycles, list, id("a"))).isEmpty();
		assertThat(Stats.changes(cycles, list, id("zzz"))).isEmpty();
	}

	// ── R-STA-01: overview ──────────────────────────────────────

	@Test
	void overviewOfACycleUsesItsDaysElapsed() {
		CycleData cycle = cyc("a", "2026-05-01", "1000", "2026-05-31");
		List<EntryData> list = List.of(exp("1", "2026-05-02", "100"), exp("2", "2026-05-09", "46.25"),
				income("i", "2026-05-03", "50"), deleted(exp("x", "2026-05-04", "999")));
		CycleSummary summary = CycleSummary.of(List.of(cycle), cycle, list, d("2026-05-10"));
		Stats.Overview o = Stats.overview(list, summary, d("2026-05-10"));
		assertThat(o.total()).isEqualByComparingTo("146.25");
		assertThat(o.dailyAverage()).isEqualByComparingTo("14.63"); // 146.25 ÷ 10 days
		assertThat(o.count()).isEqualTo(2);
		assertThat(o.extra()).isEqualByComparingTo("50");
		assertThat(o.firstDay()).isEqualTo(d("2026-05-01"));
		assertThat(o.endDay()).isEqualTo(d("2026-05-10"));
	}

	@Test
	void overviewOfNoCycleCountsDaysFromTheOldestEntryToTheNewest() {
		List<EntryData> list = List.of(exp("1", "2026-04-01", "30"), exp("2", "2026-04-10", "70"));
		Stats.Overview o = Stats.overview(list, null, d("2026-05-10"));
		assertThat(o.dailyAverage()).isEqualByComparingTo("10.00"); // 100 ÷ 10 days
		assertThat(o.firstDay()).isEqualTo(d("2026-04-01"));
		assertThat(o.endDay()).isEqualTo(d("2026-04-10"));

		Stats.Overview empty = Stats.overview(List.of(), null, d("2026-05-10"));
		assertThat(empty.count()).isZero();
		assertThat(empty.total()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(empty.endDay()).isEqualTo(d("2026-05-10"));
	}
}
