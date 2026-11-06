package com.abdullah.msal2.expenses;

import static com.abdullah.msal2.Fixtures.createdAt;
import static com.abdullah.msal2.Fixtures.d;
import static com.abdullah.msal2.Fixtures.deleted;
import static com.abdullah.msal2.Fixtures.exp;
import static com.abdullah.msal2.Fixtures.id;
import static com.abdullah.msal2.Fixtures.ids;
import static com.abdullah.msal2.Fixtures.inCategory;
import static com.abdullah.msal2.Fixtures.income;
import static com.abdullah.msal2.Fixtures.m;
import static com.abdullah.msal2.Fixtures.named;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.expenses.QuickPicks.CategoryRef;
import com.abdullah.msal2.expenses.QuickPicks.Shortcut;

/**
 * R-ENT-14 (one-tap shortcuts) and R-ENT-15 (category order), with the examples of
 * BUSINESS_RULES.md. New rules (DESIGN.md D-20), not in M-SAL.
 */
class QuickPicksTest {

	private static final LocalDate TODAY = d("2026-11-11");

	private static final List<CategoryRef> CATEGORIES = List.of(
			new CategoryRef(id("food"), false, 1), new CategoryRef(id("coffee"), false, 2),
			new CategoryRef(id("car"), false, 3), new CategoryRef(id("kids"), false, 4),
			new CategoryRef(id("old"), true, 5));

	private static int seq;

	/** An expense, created at 10:00 on its date (plus a step, so creation order is clear). */
	private static EntryData e(String name, String category, String amount, String date) {
		EntryData entry = named(inCategory(exp("e" + seq++, date, amount), category), name);
		return createdAt(entry, Instant.parse(date + "T10:00:00Z").toEpochMilli() + seq);
	}

	private static List<EntryData> times(int n, String name, String category, String amount, String firstDate) {
		List<EntryData> list = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			list.add(e(name, category, amount, d(firstDate).plusDays(i).toString()));
		}
		return list;
	}

	private static List<Shortcut> shortcuts(List<EntryData> entries) {
		return QuickPicks.of(entries, CATEGORIES, TODAY).shortcuts();
	}

	private static Shortcut s(String name, String category, String amount) {
		return new Shortcut(name, id(category), m(amount));
	}

	@Test
	void repeatedOnesOnlyMostRepeatedFirst() {
		List<EntryData> list = new ArrayList<>();
		list.addAll(times(3, "قهوة", "coffee", "18.00", "2026-11-01"));
		list.addAll(times(2, "بنزين", "car", "60.00", "2026-11-01"));
		list.add(e("غدا", "food", "35.00", "2026-11-05"));
		assertThat(shortcuts(list)).containsExactly(s("قهوة", "coffee", "18.00"), s("بنزين", "car", "60.00"));
	}

	@Test
	void spellingsAreOneShortcutWithTheLatestSpelling() {
		List<EntryData> list = new ArrayList<>(times(2, "قهوة", "coffee", "18.00", "2026-11-01"));
		list.add(e("قهوه", "coffee", "18.00", "2026-11-05"));
		assertThat(shortcuts(list)).containsExactly(s("قهوه", "coffee", "18.00"));
	}

	@Test
	void anotherAmountIsAnotherShortcut() {
		List<EntryData> list = new ArrayList<>(times(2, "قهوة", "coffee", "18.00", "2026-11-01"));
		list.addAll(times(2, "قهوة", "coffee", "20.00", "2026-11-05"));
		// same count: the more recent first
		assertThat(shortcuts(list)).containsExactly(s("قهوة", "coffee", "20.00"), s("قهوة", "coffee", "18.00"));
	}

	@Test
	void onlyTheLast90DaysOfLiveExpensesInShownCategories() {
		List<EntryData> list = new ArrayList<>();
		// 100 days ago: out of the window
		list.addAll(times(5, "قهوة", "coffee", "18.00", TODAY.minusDays(104).toString()));
		// income never counts
		list.add(named(inCategory(income("i1", "2026-11-01", "500.00"), "food"), "مكافأة"));
		list.add(named(inCategory(income("i2", "2026-11-02", "500.00"), "food"), "مكافأة"));
		// deleted entries never count
		list.add(deleted(e("غدا", "food", "35.00", "2026-11-01")));
		list.add(deleted(e("غدا", "food", "35.00", "2026-11-02")));
		// a hidden category offers no shortcut
		list.addAll(times(3, "لعبة", "old", "10.00", "2026-11-01"));
		// day 90 counts (today and the 89 days before it)
		list.addAll(times(2, "بنزين", "car", "60.00", TODAY.minusDays(89).toString()));
		assertThat(shortcuts(list)).containsExactly(s("بنزين", "car", "60.00"));
	}

	@Test
	void atMostFour() {
		List<EntryData> list = new ArrayList<>();
		for (int i = 1; i <= 6; i++) {
			list.addAll(times(2 + i, "شي" + i, "food", i + ".00", "2026-11-01"));
		}
		assertThat(shortcuts(list)).extracting(Shortcut::name).containsExactly("شي6", "شي5", "شي4", "شي3");
	}

	@Test
	void categoriesByUseThenThePersonsOrderWithoutHiddenOnes() {
		List<EntryData> list = new ArrayList<>();
		list.addAll(times(3, "بنزين", "car", "60.00", "2026-11-01"));
		list.addAll(times(2, "قهوة", "coffee", "18.00", "2026-11-01"));
		// income does not count as use
		list.add(named(inCategory(income("i1", "2026-11-01", "500.00"), "kids"), "عيدية"));
		list.add(named(inCategory(income("i2", "2026-11-02", "500.00"), "kids"), "عيدية"));
		list.add(named(inCategory(income("i3", "2026-11-03", "500.00"), "kids"), "عيدية"));
		assertThat(QuickPicks.of(list, CATEGORIES, TODAY).categoryOrder())
				.containsExactlyElementsOf(ids("car", "coffee", "food", "kids"));
	}

	@Test
	void withNoHistoryNoShortcutsAndThePersonsOrder() {
		QuickPicks.Result picks = QuickPicks.of(List.of(), CATEGORIES, TODAY);
		assertThat(picks.shortcuts()).isEmpty();
		assertThat(picks.categoryOrder()).containsExactlyElementsOf(ids("food", "coffee", "car", "kids"));
	}
}
