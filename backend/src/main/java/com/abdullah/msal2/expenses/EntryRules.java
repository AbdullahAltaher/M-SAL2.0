package com.abdullah.msal2.expenses;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Small rules about single entries (R-ENT-01, R-ENT-02, R-ENT-08).
 */
public final class EntryRules {

	private EntryRules() {
	}

	/** The description without surrounding spaces, as it is saved (R-ENT-02). */
	public static String cleanName(String name) {
		return name == null ? "" : Text.trim(name);
	}

	/** A new entry needs a description; spaces alone do not count (R-ENT-02). */
	public static boolean hasName(String name) {
		return !cleanName(name).isEmpty();
	}

	/** The description, or the category name when it is empty (old data, R-ENT-01). */
	public static String displayName(EntryData entry, Function<UUID, String> categoryName) {
		String name = cleanName(entry.name());
		return name.isEmpty() ? categoryName.apply(entry.categoryId()) : entry.name();
	}

	/**
	 * The expense for "repeat last" (R-ENT-08): the newest created, income excluded; on equal
	 * creation times the later date wins.
	 */
	public static Optional<EntryData> lastExpense(List<EntryData> entries) {
		return entries.stream()
				.filter(e -> e.isLive() && e.isExpense())
				.max(Comparator.comparing(EntryData::createdAt).thenComparing(EntryData::date));
	}
}
