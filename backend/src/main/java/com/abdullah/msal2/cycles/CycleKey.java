package com.abdullah.msal2.cycles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * The {@code cycle} parameter of the expense endpoints (API.md SRV-001): {@code latest} (the
 * default), {@code none} for "no cycle", or a cycle id.
 */
public final class CycleKey {

	public static final String LATEST = "latest";
	public static final String NONE = "none";

	private CycleKey() {
	}

	/**
	 * The selected cycle, or empty for "no cycle". {@code latest} with no cycles is "no cycle".
	 * An id that is not one of the account's cycles is {@code SYS-E004}.
	 */
	public static Optional<CycleData> resolve(String key, List<CycleData> cycles) {
		if (key == null || key.isEmpty() || key.equals(LATEST)) {
			List<CycleData> sorted = Cycles.sorted(cycles);
			return sorted.isEmpty() ? Optional.empty() : Optional.of(sorted.getLast());
		}
		if (key.equals(NONE)) {
			return Optional.empty();
		}
		UUID id;
		try {
			id = UUID.fromString(key);
		}
		catch (IllegalArgumentException e) {
			throw new ApiException(ErrorCode.SYS_E006);
		}
		return Optional.of(cycles.stream().filter(c -> c.id().equals(id)).findFirst()
				.orElseThrow(() -> new ApiException(ErrorCode.SYS_E004)));
	}

	/** The key of the cycle an entry falls in, so the screen can jump to it. */
	public static String of(List<CycleData> cycles, EntryData entry) {
		return Cycles.forDate(cycles, entry.date()).map(c -> c.id().toString()).orElse(NONE);
	}

	/**
	 * Every key the account can show, oldest first: {@code none} when there are entries before
	 * the first cycle or no cycles at all, then each cycle.
	 */
	public static List<String> all(List<CycleData> cycles, List<EntryData> entries) {
		List<String> keys = new ArrayList<>();
		if (cycles.isEmpty() || !Cycles.bucket(cycles, entries).noCycle().isEmpty()) {
			keys.add(NONE);
		}
		Cycles.sorted(cycles).forEach(c -> keys.add(c.id().toString()));
		return keys;
	}

	/** The live entries of the selected cycle, or of "no cycle". */
	public static List<EntryData> entriesOf(Optional<CycleData> selected, List<CycleData> cycles,
			List<EntryData> entries) {
		Cycles.Buckets buckets = Cycles.bucket(cycles, entries);
		List<EntryData> list = selected.map(c -> buckets.byCycle().get(c.id())).orElse(buckets.noCycle());
		return list.stream().filter(EntryData::isLive).toList();
	}
}
