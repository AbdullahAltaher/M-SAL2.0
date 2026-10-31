package com.abdullah.msal2.cycles;

import static com.abdullah.msal2.Fixtures.cyc;
import static com.abdullah.msal2.Fixtures.d;
import static com.abdullah.msal2.Fixtures.deleted;
import static com.abdullah.msal2.Fixtures.exp;
import static com.abdullah.msal2.Fixtures.id;
import static com.abdullah.msal2.Fixtures.ids;
import static com.abdullah.msal2.Fixtures.idsOf;
import static com.abdullah.msal2.Fixtures.m;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.system.time.Today;

/**
 * R-CYC-01, R-CYC-02, R-CYC-03 (duplicate dates) and R-CYC-07, ported from M-SAL's
 * test/cycles.test.js and test/expenses.test.js with the same data and results.
 * <p>
 * Not ported, because Java's {@code LocalDate} replaces the old string-date code: parseDate,
 * addDays, daysBetween, the time-zone re-runs, and "entries with a broken date" (the database
 * column is a {@code date}, so it cannot hold one).
 */
class CyclesTest {

	private static Cycles.Buckets bucket(List<CycleData> cycles, EntryData... entries) {
		return Cycles.bucket(cycles, List.of(entries));
	}

	private static String owner(List<CycleData> cycles, String date) {
		return Cycles.forDate(cycles, d(date)).map(c -> c.id().toString()).orElse("none");
	}

	private static String idOf(String name) {
		return id(name).toString();
	}

	// ── R-CYC-01: the business rules example ────────────────────

	@Test
	void businessRulesExample() {
		List<CycleData> cycles = List.of(cyc("a", "2026-10-01"), cyc("b", "2026-11-01"));
		assertThat(Cycles.bounds(cycles, cycles.get(0)))
				.isEqualTo(new Cycles.Bounds(d("2026-10-01"), d("2026-11-01"), d("2026-10-31"), false));
		assertThat(Cycles.bounds(cycles, cycles.get(1)))
				.isEqualTo(new Cycles.Bounds(d("2026-11-01"), null, null, true));
		assertThat(owner(cycles, "2026-10-15")).isEqualTo(idOf("a"));
		assertThat(owner(cycles, "2026-09-20")).isEqualTo("none");
	}

	// ── Boundaries ──────────────────────────────────────────────

	@Test
	void expenseOnTheBoundaryBelongsToTheNewCycle() {
		List<CycleData> cycles = List.of(cyc("a", "2026-01-27"), cyc("b", "2026-02-27"));
		Cycles.Buckets b = bucket(cycles, exp("last", "2026-02-26", "1"), exp("boundary", "2026-02-27", "2"),
				exp("first", "2026-01-27", "3"));
		assertThat(idsOf(b.byCycle().get(id("a")))).containsExactlyInAnyOrderElementsOf(ids("first", "last"));
		assertThat(idsOf(b.byCycle().get(id("b")))).containsExactlyElementsOf(ids("boundary"));
		assertThat(b.noCycle()).isEmpty();
	}

	@Test
	void closedCycleEndsTheDayBeforeTheNextStartAndTheLatestIsOpen() {
		List<CycleData> cycles = List.of(cyc("b", "2026-02-27"), cyc("a", "2026-01-27")); // deliberately unsorted
		assertThat(Cycles.bounds(cycles, cycles.get(1)))
				.isEqualTo(new Cycles.Bounds(d("2026-01-27"), d("2026-02-27"), d("2026-02-26"), false));
		assertThat(Cycles.bounds(cycles, cycles.get(0)))
				.isEqualTo(new Cycles.Bounds(d("2026-02-27"), null, null, true));
	}

	@Test
	void firstDayAndTheDayBeforeTheNextSalaryBothCount() {
		List<CycleData> cycles = List.of(cyc("a", "2026-03-01"), cyc("b", "2026-03-31"));
		assertThat(owner(cycles, "2026-03-01")).isEqualTo(idOf("a"));
		assertThat(owner(cycles, "2026-03-30")).isEqualTo(idOf("a"));
		assertThat(owner(cycles, "2026-03-31")).isEqualTo(idOf("b"));
		assertThat(owner(cycles, "2030-01-01")).isEqualTo(idOf("b")); // the open cycle has no upper limit
	}

	@Test
	void expenseEnteredAt0030InDubaiLandsInTheCycleStartingToday() {
		List<CycleData> cycles = List.of(cyc("a", "2026-09-02"), cyc("b", "2026-10-02"));
		// 00:30 on 2 Oct in Dubai
		String today = Today.of(Instant.parse("2026-10-01T20:30:00Z"), "Asia/Dubai").toString();
		assertThat(owner(cycles, today)).isEqualTo(idOf("b"));
	}

	// ── Editing a cycle date (R-CYC-07) ─────────────────────────

	@Test
	void editingACycleDateRebucketsEntriesWithoutChangingThem() {
		List<EntryData> entries = List.of(exp("e1", "2026-02-20", "10"), exp("e2", "2026-02-25", "20"),
				exp("e3", "2026-02-28", "30"));
		List<CycleData> cycles = List.of(cyc("a", "2026-01-27"), cyc("b", "2026-02-27"));
		Cycles.Buckets b = Cycles.bucket(cycles, entries);
		assertThat(idsOf(b.byCycle().get(id("a")))).containsExactlyElementsOf(ids("e1", "e2"));
		assertThat(idsOf(b.byCycle().get(id("b")))).containsExactlyElementsOf(ids("e3"));

		// The salary actually arrived earlier
		cycles = List.of(cycles.get(0), cycles.get(1).withStartDate(d("2026-02-24")));
		b = Cycles.bucket(cycles, entries);
		assertThat(idsOf(b.byCycle().get(id("a")))).containsExactlyElementsOf(ids("e1"));
		assertThat(idsOf(b.byCycle().get(id("b")))).containsExactlyElementsOf(ids("e2", "e3"));

		// And later
		cycles = List.of(cycles.get(0), cycles.get(1).withStartDate(d("2026-03-15")));
		b = Cycles.bucket(cycles, entries);
		assertThat(idsOf(b.byCycle().get(id("a")))).containsExactlyElementsOf(ids("e1", "e2", "e3"));
		assertThat(b.byCycle().get(id("b"))).isEmpty();
	}

	@Test
	void editingTheFirstCycleMovesEntriesInOrOutOfNoCycle() {
		List<EntryData> entries = List.of(exp("e1", "2026-02-05", "5"), exp("e2", "2026-02-12", "6"));
		Cycles.Buckets b = Cycles.bucket(List.of(cyc("a", "2026-02-10")), entries);
		assertThat(idsOf(b.noCycle())).containsExactlyElementsOf(ids("e1"));
		b = Cycles.bucket(List.of(cyc("a", "2026-02-01")), entries);
		assertThat(b.noCycle()).isEmpty();
		assertThat(idsOf(b.byCycle().get(id("a")))).containsExactlyElementsOf(ids("e1", "e2"));
	}

	@Test
	void movedEntryCountBeforeSaving() {
		List<CycleData> cycles = List.of(cyc("a", "2026-01-27"), cyc("b", "2026-02-27"));
		List<EntryData> list = List.of(exp("1", "2026-02-20", "1"), exp("2", "2026-02-25", "1"),
				exp("3", "2026-02-28", "1"));
		assertThat(Cycles.movedEntryCount(cycles, list, id("b"), d("2026-02-24"))).isEqualTo(1);
		assertThat(Cycles.movedEntryCount(cycles, list, id("b"), d("2026-02-27"))).isZero();
		assertThat(Cycles.movedEntryCount(cycles, list, id("b"), d("2026-03-15"))).isEqualTo(1);
		// The first cycle moving later pushes early entries to "no cycle"
		assertThat(Cycles.movedEntryCount(cycles, list, id("a"), d("2026-02-22"))).isEqualTo(1);
		// A duplicate date is refused, so nothing moves
		assertThat(Cycles.movedEntryCount(cycles, list, id("b"), d("2026-01-27"))).isZero();
		assertThat(Cycles.movedEntryCount(cycles, list, id("zzz"), d("2026-02-24"))).isZero();
	}

	@Test
	void movedEntryCountBusinessRulesExampleIgnoresDeletedEntries() {
		List<CycleData> cycles = List.of(cyc("a", "2026-10-01"), cyc("b", "2026-11-01"));
		List<EntryData> list = List.of(exp("1", "2026-10-28", "1"), exp("2", "2026-10-30", "1"),
				exp("3", "2026-11-02", "1"), deleted(exp("gone", "2026-10-29", "1")));
		assertThat(Cycles.movedEntryCount(cycles, list, id("b"), d("2026-10-28"))).isEqualTo(2);
	}

	@Test
	void deletingACycleMovesItsEntriesToThePreviousCycleOrNoCycle() {
		// R-CYC-08: entries are never deleted with their cycle
		List<EntryData> entries = List.of(exp("e1", "2026-01-30", "1"), exp("e2", "2026-02-28", "1"));
		List<CycleData> both = List.of(cyc("a", "2026-01-27"), cyc("b", "2026-02-27"));
		assertThat(idsOf(Cycles.bucket(both.subList(0, 1), entries).byCycle().get(id("a"))))
				.as("b deleted: its entry moves to a").containsExactlyElementsOf(ids("e1", "e2"));
		Cycles.Buckets firstDeleted = Cycles.bucket(both.subList(1, 2), entries);
		assertThat(idsOf(firstDeleted.noCycle())).as("a deleted: its entry moves to no cycle")
				.containsExactlyElementsOf(ids("e1"));
		assertThat(idsOf(firstDeleted.byCycle().get(id("b")))).containsExactlyElementsOf(ids("e2"));
	}

	// ── Before the first cycle ──────────────────────────────────

	@Test
	void expenseBeforeTheEarliestCycleGoesToNoCycle() {
		List<CycleData> cycles = List.of(cyc("a", "2026-03-05"), cyc("b", "2026-04-05"));
		Cycles.Buckets b = bucket(cycles, exp("old", "2026-03-04", "9"), exp("older", "2020-01-01", "1"),
				exp("ok", "2026-03-05", "2"));
		assertThat(idsOf(b.noCycle())).containsExactlyInAnyOrderElementsOf(ids("old", "older"));
		assertThat(idsOf(b.byCycle().get(id("a")))).containsExactlyElementsOf(ids("ok"));
		assertThat(Cycles.forDate(cycles, d("2026-03-04"))).isEmpty();
	}

	@Test
	void withNoCyclesEveryEntryIsNoCycleAndNothingIsLost() {
		Cycles.Buckets b = bucket(List.of(), exp("1", "2026-05-01", "1"), exp("2", "2026-05-02", "2"));
		assertThat(b.noCycle()).hasSize(2);
		assertThat(b.byCycle()).isEmpty();
	}

	// ── Duplicate dates (R-CYC-03) ──────────────────────────────

	@Test
	void twoCyclesOnTheSameDateAreRefused() {
		List<CycleData> cycles = List.of(cyc("a", "2026-04-27"), cyc("b", "2026-05-27"));
		assertThat(Cycles.isDuplicateDate(cycles, d("2026-05-27"), null)).isTrue(); // adding
		assertThat(Cycles.isDuplicateDate(cycles, d("2026-04-27"), id("b"))).isTrue(); // editing b
		assertThat(Cycles.isDuplicateDate(cycles, d("2026-05-27"), id("b"))).isFalse(); // re-saving b's own date
		assertThat(Cycles.isDuplicateDate(cycles, d("2026-06-27"), null)).isFalse();
	}

	@Test
	void duplicateDatesFromOldDataStillBucketTheSameWayAndLoseNothing() {
		List<CycleData> cycles = List.of(cyc("z", "2026-06-01"), cyc("a", "2026-06-01"));
		List<EntryData> entries = List.of(exp("e", "2026-06-05", "1"), exp("f", "2026-05-31", "1"));
		Cycles.Buckets b1 = Cycles.bucket(cycles, entries);
		Cycles.Buckets b2 = Cycles.bucket(cycles.reversed(), entries);
		assertThat(b1).isEqualTo(b2);
		assertThat(b1.byCycle().get(id("z")).size() + b1.byCycle().get(id("a")).size()).isEqualTo(1);
		assertThat(idsOf(b1.noCycle())).containsExactlyElementsOf(ids("f"));
	}

	// ── New cycle (R-CYC-02) ────────────────────────────────────

	@Test
	void prefillTakesTodayAndTheLatestAllowance() {
		List<CycleData> cycles = List.of(cyc("b", "2026-02-27", "4000"), cyc("a", "2026-01-27", "3000"));
		assertThat(Cycles.prefill(cycles, d("2026-03-27"))).isEqualTo(new Cycles.Prefill(d("2026-03-27"), m("4000.00")));
		assertThat(Cycles.prefill(List.of(), d("2026-03-27"))).isEqualTo(new Cycles.Prefill(d("2026-03-27"), m("0.00")));
	}

	// ── Month and year ends ─────────────────────────────────────

	@Test
	void cyclesStartingOnThe31st28thOr29thCrossMonthEnds() {
		List<CycleData> cycles = List.of(cyc("a", "2026-01-31"), cyc("b", "2026-02-28"), cyc("c", "2026-03-31"));
		assertThat(owner(cycles, "2026-02-27")).isEqualTo(idOf("a"));
		assertThat(owner(cycles, "2026-02-28")).isEqualTo(idOf("b"));
		assertThat(owner(cycles, "2026-03-30")).isEqualTo(idOf("b"));
		assertThat(owner(cycles, "2026-03-31")).isEqualTo(idOf("c"));
		assertThat(Cycles.bounds(cycles, cycles.get(0)).lastDay()).isEqualTo(d("2026-02-27"));
		assertThat(Cycles.bounds(cycles, cycles.get(1)).lastDay()).isEqualTo(d("2026-03-30"));
	}

	@Test
	void aCycleSpansCalendarMonthsWithoutRollingOver() {
		List<CycleData> cycles = List.of(cyc("a", "2026-01-27"));
		for (String date : List.of("2026-01-31", "2026-02-01", "2026-02-15", "2026-03-01", "2026-12-31", "2027-06-01")) {
			assertThat(owner(cycles, date)).as(date).isEqualTo(idOf("a"));
		}
	}

	@Test
	void yearEndAndLeapYearFebruary() {
		List<CycleData> cycles = List.of(cyc("a", "2026-12-25"), cyc("b", "2027-01-25"), cyc("c", "2028-02-29"),
				cyc("d", "2028-03-01"));
		assertThat(owner(cycles, "2027-01-24")).isEqualTo(idOf("a"));
		assertThat(owner(cycles, "2027-01-01")).isEqualTo(idOf("a"));
		assertThat(owner(cycles, "2028-02-28")).isEqualTo(idOf("b"));
		assertThat(owner(cycles, "2028-02-29")).isEqualTo(idOf("c"));
		assertThat(owner(cycles, "2028-03-01")).isEqualTo(idOf("d"));
		CycleSummary leap = CycleSummary.of(cycles, cycles.get(2), List.of(), d("2028-03-05"));
		assertThat(leap.totalDays()).isEqualTo(1);
		assertThat(leap.daysElapsed()).isEqualTo(1);
		assertThat(CycleSummary.of(cycles, cycles.get(0), List.of(), d("2028-03-05")).totalDays()).isEqualTo(31);
	}
}
