package com.abdullah.msal2.expenses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Description suggestions for a category (R-ENT-12), from M-SAL's {@code suggestNames}.
 * <p>
 * The most used names first, then the most recently used, then alphabetical. Spelling variants
 * (أ/ا, ة/ه, case, as in {@link EntrySearch#key}) count as one name and show the latest spelling.
 * With no history at all, the category's starter names are offered (for food: ريوق، غدا، عشا).
 */
public final class NameSuggestions {

	public static final int DEFAULT_LIMIT = 4;

	/** Old entries first, so later ones win ties like the array order in M-SAL. */
	private static final Comparator<EntryData> OLDEST_FIRST = Comparator.comparing(EntryData::createdAt)
			.thenComparing(e -> e.id().toString());

	private static final class Group {
		String name;
		int count;
		LocalDate day;
		Instant at;
	}

	private NameSuggestions() {
	}

	/**
	 * @param starters the category's default suggestions ({@code category_templates.default_suggestions})
	 */
	public static List<String> suggest(List<EntryData> entries, UUID categoryId, int limit, List<String> starters) {
		Map<String, Group> groups = new LinkedHashMap<>();
		for (EntryData entry : entries.stream().sorted(OLDEST_FIRST).toList()) {
			if (!entry.isLive() || !categoryId.equals(entry.categoryId())) {
				continue;
			}
			String name = EntryRules.cleanName(entry.name());
			if (name.isEmpty()) {
				continue;
			}
			Group group = groups.get(EntrySearch.key(name));
			if (group == null) {
				group = new Group();
				group.name = name;
				group.count = 1;
				group.day = entry.date();
				group.at = entry.createdAt();
				groups.put(EntrySearch.key(name), group);
			}
			else {
				group.count++;
				boolean later = entry.date().isAfter(group.day)
						|| (entry.date().equals(group.day) && !entry.createdAt().isBefore(group.at));
				if (later) {
					group.name = name;
					group.day = entry.date();
					group.at = entry.createdAt();
				}
			}
		}
		if (groups.isEmpty()) {
			return starters.stream().limit(limit).toList();
		}
		return groups.values().stream()
				.sorted(Comparator.comparingInt((Group g) -> g.count).reversed()
						.thenComparing((Group g) -> g.day, Comparator.reverseOrder())
						.thenComparing((Group g) -> g.at, Comparator.reverseOrder())
						.thenComparing(g -> g.name))
				.limit(limit)
				.map(g -> g.name)
				.toList();
	}
}
