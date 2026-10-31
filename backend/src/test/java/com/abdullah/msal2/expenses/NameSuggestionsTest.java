package com.abdullah.msal2.expenses;

import static com.abdullah.msal2.Fixtures.createdAt;
import static com.abdullah.msal2.Fixtures.deleted;
import static com.abdullah.msal2.Fixtures.exp;
import static com.abdullah.msal2.Fixtures.id;
import static com.abdullah.msal2.Fixtures.inCategory;
import static com.abdullah.msal2.Fixtures.named;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * R-ENT-12, ported from M-SAL's test/expenses.test.js ({@code suggestNames}).
 */
class NameSuggestionsTest {

	private static final List<String> FOOD_STARTERS = List.of("ريوق", "غدا", "عشا");

	/** An entry created at 10:00 UTC on its own date, like the JS test helper. */
	private static EntryData e(String name, String date, String category) {
		EntryData entry = named(inCategory(exp(name + date, date, "5"), category), name);
		return createdAt(entry, Instant.parse(date + "T10:00:00Z").toEpochMilli());
	}

	private static List<String> suggest(List<EntryData> list, String category, int limit, List<String> starters) {
		return NameSuggestions.suggest(list, id(category), limit, starters);
	}

	@Test
	void mostUsedFirstThenMostRecent() {
		List<EntryData> list = List.of(e("لاتيه", "2026-09-01", "cat_coffee"), e("لاتيه", "2026-09-05", "cat_coffee"),
				e("لاتيه", "2026-09-09", "cat_coffee"), e("اسبريسو", "2026-09-02", "cat_coffee"),
				e("اسبريسو", "2026-09-08", "cat_coffee"), e("شاي", "2026-09-03", "cat_coffee"),
				e("كابتشينو", "2026-09-10", "cat_coffee"), e("موكا", "2026-08-01", "cat_coffee"));
		// Count, then recency: كابتشينو is newer than شاي
		assertThat(suggest(list, "cat_coffee", 4, List.of())).containsExactly("لاتيه", "اسبريسو", "كابتشينو", "شاي");
		assertThat(suggest(list, "cat_coffee", 2, List.of())).containsExactly("لاتيه", "اسبريسو");
		assertThat(suggest(list, "cat_coffee", 10, List.of())).hasSize(5);
	}

	@Test
	void otherCategoriesDeletedAndEmptyNamesAreIgnoredAndSpellingsMerge() {
		List<EntryData> list = List.of(createdAt(e("غداء", "2026-09-01", "cat_food"), 1),
				deleted(createdAt(e("غداء", "2026-09-02", "cat_food"), 1)),
				createdAt(e("  ", "2026-09-03", "cat_food"), 1), createdAt(e("", "2026-09-03", "cat_food"), 1),
				createdAt(e("مطعم", "2026-09-04", "cat_fun"), 1), createdAt(e("إفطار", "2026-09-05", "cat_food"), 1),
				createdAt(e("افطار", "2026-09-06", "cat_food"), 1), createdAt(e("ٱفطار", "2026-09-07", "cat_food"), 1));
		// Three افطار spellings are one name, shown with the latest spelling
		assertThat(suggest(list, "cat_food", 4, FOOD_STARTERS)).containsExactly("ٱفطار", "غداء");
		assertThat(suggest(list, "cat_fun", 4, List.of())).containsExactly("مطعم");
		assertThat(suggest(list, "cat_kids", 4, List.of())).as("no history and no starters").isEmpty();
	}

	@Test
	void withNoHistoryFoodGetsTheStarterNames() {
		assertThat(suggest(List.of(), "cat_food", 4, FOOD_STARTERS)).containsExactly("ريوق", "غدا", "عشا");
		assertThat(suggest(List.of(), "cat_food", 2, FOOD_STARTERS)).containsExactly("ريوق", "غدا");
		// Once there is history, the starter names step aside
		assertThat(suggest(List.of(e("شاورما", "2026-09-01", "cat_food")), "cat_food", 4, FOOD_STARTERS))
				.containsExactly("شاورما");
		// Deleted history does not count
		assertThat(suggest(List.of(deleted(e("غدا", "2026-09-01", "cat_food"))), "cat_food", 4, FOOD_STARTERS))
				.containsExactly("ريوق", "غدا", "عشا");
	}

	@Test
	void fullTiesFallBackToAlphabeticalSoTheChipsNeverShuffle() {
		List<EntryData> list = List.of(createdAt(e("ب", "2026-09-01", "cat_gifts"), 5),
				createdAt(e("ا", "2026-09-01", "cat_gifts"), 5), createdAt(e("ج", "2026-09-01", "cat_gifts"), 5));
		assertThat(suggest(list, "cat_gifts", 4, List.of())).containsExactly("ا", "ب", "ج");
		assertThat(suggest(list.reversed(), "cat_gifts", 4, List.of())).containsExactly("ا", "ب", "ج");
	}
}
