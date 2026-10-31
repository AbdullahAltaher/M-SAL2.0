package com.abdullah.msal2.budget;

import static com.abdullah.msal2.Fixtures.d;
import static com.abdullah.msal2.Fixtures.m;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.budget.BudgetTotals.Amount;

/**
 * R-BUD-01, R-BUD-02 and R-BUD-05, with the example in BUSINESS_RULES.md.
 */
class BudgetTotalsTest {

	private static Amount a(String value) {
		return new Amount(m(value), false);
	}

	private static Amount approx(String value) {
		return new Amount(m(value), true);
	}

	@Test
	void businessRulesExample() {
		// Salary 15,000; group 1: rent 5,000 + electricity ~400; group 2: car 2,500
		BudgetTotals.Result r = BudgetTotals.compute(a("15000"),
				List.of(List.of(a("5000"), approx("400")), List.of(a("2500"))));
		BudgetTotals.GroupTotal fixed = r.groups().get(0);
		assertThat(fixed.subtotal()).isEqualByComparingTo("5400");
		assertThat(fixed.subtotalApprox()).isTrue();
		assertThat(fixed.balanceAfter()).isEqualByComparingTo("9600");
		assertThat(fixed.balanceApprox()).isTrue();

		BudgetTotals.GroupTotal loans = r.groups().get(1);
		assertThat(loans.subtotal()).isEqualByComparingTo("2500");
		assertThat(loans.subtotalApprox()).as("no approximate item in this group").isFalse();
		assertThat(loans.balanceAfter()).isEqualByComparingTo("7100");
		assertThat(loans.balanceApprox()).as("approximate spreads down").isTrue();

		assertThat(r.finalBalance()).isEqualByComparingTo("7100");
		assertThat(r.finalApprox()).isTrue();
	}

	@Test
	void approximateDoesNotSpreadUp() {
		BudgetTotals.Result r = BudgetTotals.compute(a("1000"),
				List.of(List.of(a("100")), List.of(approx("50"))));
		assertThat(r.groups().get(0).balanceApprox()).isFalse();
		assertThat(r.groups().get(1).balanceApprox()).isTrue();
	}

	@Test
	void approximateSalaryMakesEveryBalanceApproximate() {
		BudgetTotals.Result r = BudgetTotals.compute(approx("15000"), List.of(List.of(a("100"))));
		assertThat(r.groups().get(0).subtotalApprox()).isFalse();
		assertThat(r.groups().get(0).balanceApprox()).isTrue();
		assertThat(r.finalApprox()).isTrue();
	}

	@Test
	void emptyAmountsCountAsZeroAndTheBalanceCanGoNegative() {
		BudgetTotals.Result r = BudgetTotals.compute(Amount.empty(),
				List.of(List.of(Amount.empty(), a("300")), List.of(a("-50"))));
		assertThat(r.groups().get(0).subtotal()).isEqualByComparingTo("300");
		assertThat(r.groups().get(0).balanceAfter()).isEqualByComparingTo("-300");
		assertThat(r.groups().get(1).subtotal()).as("negatives are allowed here").isEqualByComparingTo("-50");
		assertThat(r.finalBalance()).isEqualByComparingTo("-250");
		assertThat(r.finalApprox()).isFalse();
	}

	@Test
	void noGroupsLeavesTheSalary() {
		BudgetTotals.Result r = BudgetTotals.compute(a("15000"), List.of());
		assertThat(r.groups()).isEmpty();
		assertThat(r.finalBalance()).isEqualByComparingTo("15000");
	}

	@Test
	void monthLabelIsTheArabicMonthAndYear() {
		assertThat(MonthLabel.of(d("2026-11-11"))).isEqualTo("نوفمبر 2026");
		assertThat(MonthLabel.of(d("2027-01-01"))).isEqualTo("يناير 2027");
		assertThat(MonthLabel.of(d("2026-12-31"))).isEqualTo("ديسمبر 2026");
	}
}
