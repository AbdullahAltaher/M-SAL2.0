package com.abdullah.msal2.banksms;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Suggests a category for a merchant from the user's keywords (BANK-SMS.md 4.4, B-10, B-13 to
 * B-15). Pure logic, no Spring.
 * <ul>
 * <li>Names and keywords are cleaned the same way: capital letters, and every sign that is not a
 * letter or a digit ({@code -}, {@code .}, {@code /}...) is a space.</li>
 * <li>A keyword (one word or more, such as {@code RED CRESCENT}) matches whole words that follow
 * each other: {@code CAFE} does not match {@code CAFETERIA} (B-13).</li>
 * <li>The keyword found earliest in the name wins, so a restaurant inside a petrol station
 * ({@code <restaurant>-<station>}) is a restaurant (B-14); at the same place, the longer one.</li>
 * <li>If the winner has no category, the user picks one every time (B-15).</li>
 * </ul>
 */
public final class MerchantClassifier {

	private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

	/** A keyword and its category; null = "اختر الفئة كل مرة". */
	public record Keyword(String keyword, UUID categoryId) {
	}

	private MerchantClassifier() {
	}

	/** {@code "Al-Sarab  cafeteria."} → {@code "AL SARAB CAFETERIA"}. */
	public static String clean(String text) {
		if (text == null) {
			return "";
		}
		return NOT_WORD.matcher(text.toUpperCase(Locale.ROOT)).replaceAll(" ").strip();
	}

	static List<String> words(String text) {
		String cleaned = clean(text);
		return cleaned.isEmpty() ? List.of() : Arrays.asList(cleaned.split(" "));
	}

	/** The winning keyword for the name, or empty if none matches. */
	public static Optional<Keyword> match(String merchant, Collection<Keyword> keywords) {
		List<String> name = words(merchant);
		Keyword best = null;
		int bestAt = Integer.MAX_VALUE;
		int bestLength = 0;
		for (Keyword keyword : keywords) {
			List<String> words = words(keyword.keyword());
			int at = indexOf(name, words);
			if (at < 0) {
				continue;
			}
			if (at < bestAt || (at == bestAt && words.size() > bestLength)) {
				best = keyword;
				bestAt = at;
				bestLength = words.size();
			}
		}
		return Optional.ofNullable(best);
	}

	/** Where {@code words} first appear in {@code name} one after the other, or -1. */
	private static int indexOf(List<String> name, List<String> words) {
		if (words.isEmpty() || words.size() > name.size()) {
			return -1;
		}
		for (int i = 0; i + words.size() <= name.size(); i++) {
			if (name.subList(i, i + words.size()).equals(words)) {
				return i;
			}
		}
		return -1;
	}
}
