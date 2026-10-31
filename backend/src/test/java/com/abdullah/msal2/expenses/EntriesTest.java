package com.abdullah.msal2.expenses;

import static com.abdullah.msal2.Fixtures.createdAt;
import static com.abdullah.msal2.Fixtures.d;
import static com.abdullah.msal2.Fixtures.deleted;
import static com.abdullah.msal2.Fixtures.exp;
import static com.abdullah.msal2.Fixtures.id;
import static com.abdullah.msal2.Fixtures.ids;
import static com.abdullah.msal2.Fixtures.idsOf;
import static com.abdullah.msal2.Fixtures.inCategory;
import static com.abdullah.msal2.Fixtures.income;
import static com.abdullah.msal2.Fixtures.named;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * R-ENT-01, R-ENT-02, R-ENT-08, R-ENT-10 and R-ENT-11, ported from M-SAL's test/expenses.test.js.
 */
class EntriesTest {

	// ── R-ENT-10: grouping by day ───────────────────────────────

	@Test
	void newestDayFirstAndTheDayTotalCountsExpensesOnly() {
		List<EntryData> list = List.of(exp("a", "2026-10-01", "10"), exp("b", "2026-10-02", "5.5"),
				exp("c", "2026-10-02", "4.5"), income("i", "2026-10-02", "100"), exp("d", "2026-09-30", "1"));
		List<EntryDays.Day> days = EntryDays.group(list);
		assertThat(days).extracting(EntryDays.Day::date)
				.containsExactly(d("2026-10-02"), d("2026-10-01"), d("2026-09-30"));
		assertThat(days.getFirst().spent()).isEqualByComparingTo("10");
		assertThat(days.getFirst().extra()).isEqualByComparingTo("100");
		assertThat(days.getFirst().entries()).hasSize(3);
	}

	@Test
	void insideADayTheNewestEntryIsFirstWhateverTheInputOrder() {
		List<EntryData> list = List.of(createdAt(exp("a", "2026-10-02", "1"), 10),
				createdAt(exp("b", "2026-10-02", "1"), 30), createdAt(exp("c", "2026-10-02", "1"), 20));
		assertThat(idsOf(EntryDays.group(list).getFirst().entries())).containsExactlyElementsOf(ids("b", "c", "a"));
		assertThat(idsOf(EntryDays.group(list.reversed()).getFirst().entries()))
				.containsExactlyElementsOf(ids("b", "c", "a"));
	}

	// ── R-ENT-11: search and filter ─────────────────────────────

	@Test
	void searchByNameOrCategoryWithArabicNormalization() {
		Map<UUID, String> names = Map.of(id("cat_food"), "أكل ومطاعم", id("cat_coffee"), "قهوة وكافيهات",
				id("cat_edu"), "تعليم");
		Function<UUID, String> categoryName = c -> names.getOrDefault(c, "أخرى");
		List<EntryData> list = List.of(named(inCategory(exp("1", "2026-10-02", "5"), "cat_coffee"), "لاتيه"),
				named(inCategory(exp("2", "2026-10-02", "9"), "cat_food"), "غداء إدارة"),
				named(inCategory(exp("3", "2026-10-02", "7"), "cat_edu"), "Course 101"));
		Function<String, List<UUID>> find = q -> idsOf(EntrySearch.filter(list, q, null, categoryName));

		assertThat(find.apply("لاتيه")).containsExactlyElementsOf(ids("1"));
		assertThat(find.apply("قهوه")).as("ة and ه are the same").containsExactlyElementsOf(ids("1"));
		assertThat(find.apply("اداره")).as("إ and ا are the same").containsExactlyElementsOf(ids("2"));
		assertThat(find.apply("مطاعم")).as("matches the category name").containsExactlyElementsOf(ids("2"));
		assertThat(find.apply("course")).as("case-insensitive").containsExactlyElementsOf(ids("3"));
		assertThat(find.apply("١٠١")).as("Arabic-Indic digits match Latin").containsExactlyElementsOf(ids("3"));
		assertThat(find.apply("")).containsExactlyElementsOf(ids("1", "2", "3"));
		assertThat(find.apply("لاتيهً")).as("tashkeel ignored").containsExactlyElementsOf(ids("1"));

		assertThat(idsOf(EntrySearch.filter(list, "", id("cat_edu"), categoryName))).containsExactlyElementsOf(ids("3"));
		assertThat(EntrySearch.filter(list, "غداء", id("cat_edu"), categoryName)).isEmpty();
	}

	@Test
	void searchKeyMatchesTheBusinessRulesExample() {
		assertThat(EntrySearch.key("قَهْوَة")).isEqualTo(EntrySearch.key("قهوه"));
		assertThat(EntrySearch.key("  أإآٱ ى ـة  ")).isEqualTo("اااا ي ه");
	}

	// ── R-ENT-01, R-ENT-02 ──────────────────────────────────────

	@Test
	void displayNameFallsBackToTheCategory() {
		Function<UUID, String> categoryName = c -> "قهوة وكافيهات";
		assertThat(EntryRules.displayName(named(exp("1", "2026-10-02", "5"), "لاتيه"), categoryName)).isEqualTo("لاتيه");
		assertThat(EntryRules.displayName(exp("2", "2026-10-02", "5"), categoryName)).isEqualTo("قهوة وكافيهات");
	}

	@Test
	void aDescriptionNeedsMoreThanSpaces() {
		for (String blank : new String[] { "", "   ", "\t \n", " ", null }) {
			assertThat(EntryRules.hasName(blank)).as(String.valueOf(blank)).isFalse();
		}
		assertThat(EntryRules.hasName("  عشا  ")).isTrue();
		assertThat(EntryRules.cleanName("  عشا  ")).isEqualTo("عشا");
	}

	// ── R-ENT-08: repeat the last expense ───────────────────────

	@Test
	void lastExpenseIsTheNewestCreatedAndSkipsIncomeAndDeleted() {
		List<EntryData> list = List.of(createdAt(exp("old", "2026-10-05", "1"), 10),
				createdAt(exp("new", "2026-10-01", "1"), 30), createdAt(income("inc", "2026-10-06", "1"), 50),
				deleted(createdAt(exp("gone", "2026-10-06", "1"), 60)));
		assertThat(EntryRules.lastExpense(list)).get().extracting(EntryData::id).isEqualTo(id("new"));
	}

	@Test
	void lastExpenseTieOnCreationGoesToTheLaterDate() {
		List<EntryData> list = List.of(createdAt(exp("early", "2026-10-01", "1"), 30),
				createdAt(exp("late", "2026-10-03", "1"), 30));
		assertThat(EntryRules.lastExpense(list)).get().extracting(EntryData::id).isEqualTo(id("late"));
		assertThat(EntryRules.lastExpense(List.of(income("i", "2026-10-01", "1")))).isEmpty();
	}
}
