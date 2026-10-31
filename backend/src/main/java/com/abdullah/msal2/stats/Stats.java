package com.abdullah.msal2.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.abdullah.msal2.cycles.CycleData;
import com.abdullah.msal2.cycles.CycleSummary;
import com.abdullah.msal2.cycles.Cycles;
import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.system.money.Money;

/**
 * The statistics of one cycle or of "no cycle" (R-STA-01 to R-STA-05), from M-SAL's
 * {@code renderStatsView}, {@code lastDays}, {@code cycleTrend} and {@code categoryChanges}.
 */
public final class Stats {

	public static final int LAST_DAYS = 7;
	public static final int TREND_CYCLES = 6;

	private Stats() {
	}

	/**
	 * The totals card (R-STA-01), and the date range the "last 7 days" chart ends at.
	 *
	 * @param dailyAverage total ÷ days elapsed (at least 1); for "no cycle", the days from the
	 *                     oldest entry to the newest
	 */
	public record Overview(BigDecimal total, BigDecimal dailyAverage, int count, BigDecimal extra,
			LocalDate firstDay, LocalDate endDay) {
	}

	public record DayTotal(LocalDate date, BigDecimal total) {
	}

	public record CycleTotal(UUID cycleId, LocalDate start, BigDecimal total) {
	}

	public enum ChangeKind {
		NEW, UP, DOWN
	}

	public record CategoryChange(UUID categoryId, BigDecimal current, BigDecimal previous, BigDecimal delta,
			ChangeKind kind) {
	}

	/**
	 * @param entries the live entries of the selected cycle or of "no cycle"
	 * @param summary the cycle's summary, or null for "no cycle"
	 */
	public static Overview overview(List<EntryData> entries, CycleSummary summary, LocalDate today) {
		List<EntryData> live = entries.stream().filter(EntryData::isLive).toList();
		List<EntryData> expenses = live.stream().filter(EntryData::isExpense).toList();
		BigDecimal total = Money.sum(expenses.stream().map(EntryData::amount).toList());
		BigDecimal extra = Money.sum(live.stream().filter(EntryData::isIncome).map(EntryData::amount).toList());

		LocalDate firstDay;
		LocalDate endDay;
		long days;
		if (summary != null) {
			firstDay = summary.start();
			endDay = summary.lastDay() != null ? summary.lastDay() : today;
			days = Math.max(1, summary.daysElapsed());
			if (endDay.isBefore(firstDay)) {
				endDay = firstDay;
			}
		}
		else {
			List<LocalDate> dates = live.stream().map(EntryData::date).sorted().toList();
			firstDay = dates.isEmpty() ? today : dates.getFirst();
			endDay = dates.isEmpty() ? today : dates.getLast();
			days = Math.max(1, ChronoUnit.DAYS.between(firstDay, endDay) + 1);
		}
		return new Overview(total, Money.divide(total, days), expenses.size(), extra, firstDay, endDay);
	}

	/** Spending per day for up to {@code n} days ending at {@code endDay}, never before {@code firstDay} (R-STA-03). */
	public static List<DayTotal> lastDays(List<EntryData> entries, LocalDate firstDay, LocalDate endDay, int n) {
		LocalDate start = endDay.minusDays(n - 1L);
		if (start.isBefore(firstDay)) {
			start = firstDay;
		}
		Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
		for (LocalDate day = start; !day.isAfter(endDay); day = day.plusDays(1)) {
			totals.put(day, Money.ZERO);
		}
		for (EntryData entry : entries) {
			if (entry.isLive() && entry.isExpense() && totals.containsKey(entry.date())) {
				totals.merge(entry.date(), entry.amount(), (a, b) -> Money.round(a.add(b)));
			}
		}
		return totals.entrySet().stream().map(e -> new DayTotal(e.getKey(), e.getValue())).toList();
	}

	/** Total spending of the {@code n} cycles ending at the selected one, oldest first (R-STA-04). */
	public static List<CycleTotal> trend(List<CycleData> cycles, List<EntryData> entries, UUID cycleId, int n) {
		List<CycleData> sorted = Cycles.sorted(cycles);
		int i = indexOf(sorted, cycleId);
		if (i < 0) {
			return List.of();
		}
		Cycles.Buckets buckets = Cycles.bucket(cycles, entries);
		return sorted.subList(Math.max(0, i - n + 1), i + 1).stream()
				.map(c -> new CycleTotal(c.id(), c.startDate(), Money.sum(buckets.byCycle().get(c.id()).stream()
						.filter(e -> e.isLive() && e.isExpense())
						.map(EntryData::amount)
						.toList())))
				.toList();
	}

	/**
	 * Category changes against the previous cycle (R-STA-05): NEW, UP or DOWN, biggest change
	 * first. A change of 0 is left out. Empty for the first cycle. The screen shows the first 5.
	 */
	public static List<CategoryChange> changes(List<CycleData> cycles, List<EntryData> entries, UUID cycleId) {
		List<CycleData> sorted = Cycles.sorted(cycles);
		int i = indexOf(sorted, cycleId);
		if (i <= 0) {
			return List.of();
		}
		Cycles.Buckets buckets = Cycles.bucket(cycles, entries);
		Map<UUID, BigDecimal> current = totalsByCategory(buckets.byCycle().get(sorted.get(i).id()));
		Map<UUID, BigDecimal> previous = totalsByCategory(buckets.byCycle().get(sorted.get(i - 1).id()));

		Set<UUID> categories = new LinkedHashSet<>(current.keySet());
		categories.addAll(previous.keySet());
		List<CategoryChange> changes = new ArrayList<>();
		for (UUID category : categories) {
			BigDecimal cur = current.getOrDefault(category, Money.ZERO);
			BigDecimal prev = previous.getOrDefault(category, Money.ZERO);
			BigDecimal delta = Money.round(cur.subtract(prev));
			if (delta.signum() == 0) {
				continue;
			}
			ChangeKind kind = prev.signum() == 0 ? ChangeKind.NEW : delta.signum() > 0 ? ChangeKind.UP : ChangeKind.DOWN;
			changes.add(new CategoryChange(category, cur, prev, delta, kind));
		}
		changes.sort(Comparator.comparing((CategoryChange c) -> c.delta().abs()).reversed()
				.thenComparing(c -> c.categoryId().toString()));
		return changes;
	}

	private static Map<UUID, BigDecimal> totalsByCategory(List<EntryData> entries) {
		Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
		CategoryTotals.of(entries).forEach(t -> totals.put(t.categoryId(), t.total()));
		return totals;
	}

	private static int indexOf(List<CycleData> sorted, UUID cycleId) {
		for (int i = 0; i < sorted.size(); i++) {
			if (sorted.get(i).id().equals(cycleId)) {
				return i;
			}
		}
		return -1;
	}
}
