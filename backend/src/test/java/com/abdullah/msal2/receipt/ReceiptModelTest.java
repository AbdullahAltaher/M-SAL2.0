package com.abdullah.msal2.receipt;

import static com.abdullah.msal2.Fixtures.cyc;
import static com.abdullah.msal2.Fixtures.d;
import static com.abdullah.msal2.Fixtures.deleted;
import static com.abdullah.msal2.Fixtures.exp;
import static com.abdullah.msal2.Fixtures.id;
import static com.abdullah.msal2.Fixtures.inCategory;
import static com.abdullah.msal2.Fixtures.income;
import static com.abdullah.msal2.Fixtures.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.cycles.CycleData;
import com.abdullah.msal2.expenses.EntryData;

/**
 * R-RCP-01, ported from M-SAL's test/expenses.test.js ({@code buildReceiptModel}).
 */
class ReceiptModelTest {

	private static final Map<UUID, String> NAMES = Map.of(id("cat_food"), "أكل ومطاعم", id("cat_coffee"),
			"قهوة وكافيهات", id("cat_other"), "أخرى");
	private static final Function<UUID, String> CATEGORY_NAME = c -> NAMES.getOrDefault(c, "أخرى");

	@Test
	void accountRangeAllowanceCategoriesDaysSpentAndRemaining() {
		List<CycleData> cycles = List.of(cyc("a", "2026-09-02", "3000"), cyc("b", "2026-10-02", "3200"));
		List<EntryData> entries = List.of(named(exp("1", "2026-09-05", "40"), "غداء"),
				inCategory(exp("2", "2026-09-05", "10"), "cat_coffee"), named(exp("3", "2026-09-20", "100"), "عشاء"),
				named(inCategory(income("i", "2026-09-21", "50"), "cat_other"), "مكافأة"),
				deleted(exp("x", "2026-09-22", "9")), exp("n", "2026-10-03", "77"));
		ReceiptModel r = ReceiptModel.build("مصاريفي", cycles, entries, id("a"), d("2026-10-05"), CATEGORY_NAME);

		assertThat(r.accountName()).isEqualTo("مصاريفي");
		assertThat(r.start()).isEqualTo(d("2026-09-02"));
		assertThat(r.end()).isEqualTo(d("2026-10-01"));
		assertThat(r.allowance()).isEqualByComparingTo("3000");
		assertThat(r.extra()).isEqualByComparingTo("50");
		assertThat(r.spent()).isEqualByComparingTo("150");
		assertThat(r.remaining()).isEqualByComparingTo("2900");
		assertThat(r.categories()).extracting(ReceiptModel.Category::name, c -> c.total().intValue(),
				ReceiptModel.Category::pct)
				.containsExactly(tuple("أكل ومطاعم", 140, 93), tuple("قهوة وكافيهات", 10, 7));
		assertThat(r.days()).extracting(ReceiptModel.Day::date)
				.containsExactly(d("2026-09-21"), d("2026-09-20"), d("2026-09-05"));
		assertThat(r.days().get(2).entries()).extracting(ReceiptModel.Line::name)
				.as("an empty name shows the category name").contains("قهوة وكافيهات", "غداء");
		assertThat(r.days()).flatExtracting(ReceiptModel.Day::entries)
				.as("the next cycle's entry is not included").noneMatch(l -> l.amount().intValue() == 77);
	}

	@Test
	void noCycleGroupAndOpenCycle() {
		List<CycleData> cycles = List.of(cyc("a", "2026-09-02", "3000"));
		List<EntryData> entries = List.of(exp("old", "2026-08-30", "25"), exp("cur", "2026-09-03", "5"));

		ReceiptModel none = ReceiptModel.build("مصاريفي", cycles, entries, null, d("2026-09-10"), CATEGORY_NAME);
		assertThat(none.spent()).isEqualByComparingTo("25");
		assertThat(none.start()).isNull();
		assertThat(none.remaining()).as("no allowance: extra − spent").isEqualByComparingTo("-25");

		ReceiptModel open = ReceiptModel.build("مصاريفي", cycles, entries, id("a"), d("2026-09-10"), CATEGORY_NAME);
		assertThat(open.end()).as("the open cycle ends today").isEqualTo(d("2026-09-10"));
		assertThat(open.spent()).isEqualByComparingTo("5");
	}
}
