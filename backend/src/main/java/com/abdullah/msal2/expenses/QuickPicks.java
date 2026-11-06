package com.abdullah.msal2.expenses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What the entry sheet offers first (DESIGN.md D-20): the one-tap shortcuts (R-ENT-14) and the
 * categories by how much they are used (R-ENT-15). New rules, not in M-SAL.
 * <p>
 * Both look at the budget's live expenses (not income) of the last {@link #DAYS} days up to today.
 * A shortcut is one description (spelling variants as one, as in {@link NameSuggestions}), one
 * category and one amount, used at least twice, in a category that is not hidden.
 */
public final class QuickPicks {

	public static final int DAYS = 90;
	public static final int LIMIT = 4;
	static final int MIN_USES = 2;

	/** A category as these rules see it: whether it shows, and the person's own order. */
	public record CategoryRef(UUID id, boolean hidden, int sortOrder) {
	}

	public record Shortcut(String name, UUID categoryId, BigDecimal amount) {
	}

	public record Result(List<Shortcut> shortcuts, List<UUID> categoryOrder) {
	}

	/** Old entries first, so later ones win the spelling like {@link NameSuggestions}. */
	private static final Comparator<EntryData> OLDEST_FIRST = Comparator.comparing(EntryData::date)
			.thenComparing(EntryData::createdAt)
			.thenComparing(e -> e.id().toString());

	private static final class Group {
		String name;
		UUID categoryId;
		BigDecimal amount;
		int count;
		LocalDate day;
		Instant at;
	}

	private QuickPicks() {
	}

	public static Result of(List<EntryData> entries, List<CategoryRef> categories, LocalDate today) {
		LocalDate from = today.minusDays(DAYS - 1L);
		List<EntryData> recent = entries.stream()
				.filter(e -> e.isLive() && e.isExpense() && !e.date().isBefore(from) && !e.date().isAfter(today))
				.sorted(OLDEST_FIRST)
				.toList();
		return new Result(shortcuts(recent, categories), categoryOrder(recent, categories));
	}

	private static List<Shortcut> shortcuts(List<EntryData> recent, List<CategoryRef> categories) {
		Set<UUID> shown = categories.stream().filter(c -> !c.hidden()).map(CategoryRef::id).collect(Collectors.toSet());
		Map<String, Group> groups = new LinkedHashMap<>();
		for (EntryData entry : recent) {
			String name = EntryRules.cleanName(entry.name());
			if (name.isEmpty() || !shown.contains(entry.categoryId())) {
				continue;
			}
			BigDecimal amount = entry.amount().stripTrailingZeros();
			String key = EntrySearch.key(name) + '|' + entry.categoryId() + '|' + amount.toPlainString();
			Group group = groups.computeIfAbsent(key, k -> new Group());
			group.count++;
			// entries come oldest first, so each one is the latest seen so far
			group.name = name;
			group.categoryId = entry.categoryId();
			group.amount = entry.amount();
			group.day = entry.date();
			group.at = entry.createdAt();
		}
		return groups.values().stream()
				.filter(g -> g.count >= MIN_USES)
				.sorted(Comparator.comparingInt((Group g) -> g.count).reversed()
						.thenComparing((Group g) -> g.day, Comparator.reverseOrder())
						.thenComparing((Group g) -> g.at, Comparator.reverseOrder())
						.thenComparing(g -> g.name))
				.limit(LIMIT)
				.map(g -> new Shortcut(g.name, g.categoryId, g.amount))
				.toList();
	}

	private static List<UUID> categoryOrder(List<EntryData> recent, List<CategoryRef> categories) {
		Map<UUID, Integer> uses = new HashMap<>();
		recent.forEach(e -> uses.merge(e.categoryId(), 1, Integer::sum));
		return categories.stream()
				.filter(c -> !c.hidden())
				.sorted(Comparator.comparingInt((CategoryRef c) -> uses.getOrDefault(c.id(), 0)).reversed()
						.thenComparingInt(CategoryRef::sortOrder))
				.map(CategoryRef::id)
				.toList();
	}
}
