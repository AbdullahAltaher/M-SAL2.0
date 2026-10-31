package com.abdullah.msal2.cycles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.system.money.Money;

/**
 * Which cycle owns which entry (R-CYC-01), from M-SAL's {@code @@CYCLE-LOGIC} block.
 * <p>
 * Every rule here works on the cycles of one account. An entry belongs to the latest cycle
 * that started on or before its date; entries before the first cycle are "no cycle". Nothing
 * is stored on the entry, so moving a cycle date moves entries automatically (R-CYC-07).
 */
public final class Cycles {

	/** Start date, then id, so the order is always the same. */
	static final Comparator<CycleData> ORDER = Comparator.comparing(CycleData::startDate)
			.thenComparing(c -> c.id().toString());

	private Cycles() {
	}

	/** Where a cycle starts and ends. {@code endExclusive} and {@code lastDay} are null for the open cycle. */
	public record Bounds(LocalDate start, LocalDate endExclusive, LocalDate lastDay, boolean open) {
	}

	/**
	 * Every entry in exactly one place: a cycle, or "no cycle". None can disappear.
	 *
	 * @param byCycle every cycle, oldest first, with its entries (possibly none)
	 */
	public record Buckets(Map<UUID, List<EntryData>> byCycle, List<EntryData> noCycle) {
	}

	/** The values "salary arrived" starts with (R-CYC-02). */
	public record Prefill(LocalDate startDate, BigDecimal allowance) {
	}

	public static List<CycleData> sorted(List<CycleData> cycles) {
		return cycles.stream().sorted(ORDER).toList();
	}

	/** The cycle that owns a date, or empty for "no cycle". */
	public static Optional<CycleData> forDate(List<CycleData> cycles, LocalDate date) {
		CycleData found = null;
		for (CycleData cycle : sorted(cycles)) {
			if (cycle.startDate().isAfter(date)) {
				break;
			}
			found = cycle;
		}
		return Optional.ofNullable(found);
	}

	public static Bounds bounds(List<CycleData> cycles, CycleData cycle) {
		Optional<CycleData> next = sorted(cycles).stream()
				.filter(c -> c.startDate().isAfter(cycle.startDate()))
				.findFirst();
		return next.map(n -> new Bounds(cycle.startDate(), n.startDate(), n.startDate().minusDays(1), false))
				.orElseGet(() -> new Bounds(cycle.startDate(), null, null, true));
	}

	public static Buckets bucket(List<CycleData> cycles, List<EntryData> entries) {
		List<CycleData> sorted = sorted(cycles);
		Map<UUID, List<EntryData>> byCycle = new LinkedHashMap<>();
		sorted.forEach(c -> byCycle.put(c.id(), new ArrayList<>()));
		List<EntryData> noCycle = new ArrayList<>();
		for (EntryData entry : entries) {
			forDate(sorted, entry.date())
					.ifPresentOrElse(c -> byCycle.get(c.id()).add(entry), () -> noCycle.add(entry));
		}
		return new Buckets(byCycle, noCycle);
	}

	/** Another cycle already starts on this date (R-CYC-03). Re-saving a cycle's own date is fine. */
	public static boolean isDuplicateDate(List<CycleData> cycles, LocalDate date, UUID ignoreId) {
		return cycles.stream().anyMatch(c -> c.startDate().equals(date) && !c.id().equals(ignoreId));
	}

	/** Today, and the latest cycle's allowance, or 0 with no cycles (R-CYC-02). */
	public static Prefill prefill(List<CycleData> cycles, LocalDate today) {
		List<CycleData> sorted = sorted(cycles);
		BigDecimal allowance = sorted.isEmpty() ? Money.ZERO : Money.round(sorted.getLast().allowance());
		return new Prefill(today, allowance);
	}

	/**
	 * How many live entries change cycle, from or to "no cycle" included, if a cycle moves to
	 * {@code newDate} (R-CYC-07). A duplicate date is refused, so nothing moves.
	 */
	public static int movedEntryCount(List<CycleData> cycles, List<EntryData> entries, UUID cycleId,
			LocalDate newDate) {
		if (cycles.stream().noneMatch(c -> c.id().equals(cycleId)) || isDuplicateDate(cycles, newDate, cycleId)) {
			return 0;
		}
		List<CycleData> moved = cycles.stream()
				.map(c -> c.id().equals(cycleId) ? c.withStartDate(newDate) : c)
				.toList();
		List<EntryData> live = entries.stream().filter(EntryData::isLive).toList();
		Map<UUID, UUID> before = owners(cycles, live);
		Map<UUID, UUID> after = owners(moved, live);
		return (int) live.stream().filter(e -> !Objects.equals(before.get(e.id()), after.get(e.id()))).count();
	}

	/** Entry id to cycle id, with null for "no cycle". */
	private static Map<UUID, UUID> owners(List<CycleData> cycles, List<EntryData> entries) {
		Map<UUID, UUID> owners = new HashMap<>();
		for (EntryData entry : entries) {
			owners.put(entry.id(), forDate(cycles, entry.date()).map(CycleData::id).orElse(null));
		}
		return owners;
	}
}
