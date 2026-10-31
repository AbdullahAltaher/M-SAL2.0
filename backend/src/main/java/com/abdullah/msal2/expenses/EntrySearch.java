package com.abdullah.msal2.expenses;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Search and category filter for the entries list (R-ENT-11), from M-SAL's {@code searchKey}
 * and {@code filterEntries}.
 */
public final class EntrySearch {

	/** Tashkeel (U+064B–U+0670) and tatweel (U+0640). */
	private static final Pattern MARKS = Pattern.compile("[\\u064B-\\u0670\\u0640]");
	private static final Pattern ALEFS = Pattern.compile("[أإآٱ]");

	private EntrySearch() {
	}

	/**
	 * Text normalized for comparison: Latin digits, lower case, no tashkeel or tatweel,
	 * {@code أ إ آ ٱ → ا}, {@code ى → ي}, {@code ة → ه}. So "قهوه" finds "قَهْوَة".
	 */
	public static String key(String text) {
		if (text == null) {
			return "";
		}
		String s = Text.latinDigits(text).toLowerCase(Locale.ROOT);
		s = MARKS.matcher(s).replaceAll("");
		s = ALEFS.matcher(s).replaceAll("ا").replace('ى', 'ي').replace('ة', 'ه');
		return Text.trim(s);
	}

	/**
	 * Entries whose description or category name contains the query, in the given category
	 * if one is set. An empty query matches everything.
	 *
	 * @param categoryName the display name of a category id
	 */
	public static List<EntryData> filter(List<EntryData> entries, String query, UUID categoryId,
			Function<UUID, String> categoryName) {
		String q = key(query);
		return entries.stream()
				.filter(e -> categoryId == null || categoryId.equals(e.categoryId()))
				.filter(e -> q.isEmpty() || key(e.name()).contains(q) || key(categoryName.apply(e.categoryId())).contains(q))
				.toList();
	}
}
