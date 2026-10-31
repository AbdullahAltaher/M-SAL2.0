package com.abdullah.msal2.budget;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.abdullah.msal2.system.money.Money;

/**
 * The monthly budget totals (R-BUD-01, R-BUD-02), from M-SAL's {@code computeTotals}:
 * remaining = salary − group 1 − group 2 − …
 * <p>
 * An empty amount counts as 0, negatives are allowed, and "done" never changes a total.
 * "Approximate" (~) spreads down the page: a group is approximate if one of its items is, and
 * a balance is approximate if the salary, any group above it, or its own group is.
 */
public final class BudgetTotals {

	private BudgetTotals() {
	}

	/** An amount on the budget page: {@code null} when the field is empty. */
	public record Amount(BigDecimal value, boolean approx) {

		public static Amount empty() {
			return new Amount(null, false);
		}

		BigDecimal valueOrZero() {
			return value == null ? Money.ZERO : value;
		}
	}

	public record GroupTotal(BigDecimal subtotal, boolean subtotalApprox, BigDecimal balanceAfter,
			boolean balanceApprox) {
	}

	public record Result(List<GroupTotal> groups, BigDecimal finalBalance, boolean finalApprox) {
	}

	/**
	 * @param groups the items' amounts, group by group, in page order
	 */
	public static Result compute(Amount salary, List<List<Amount>> groups) {
		BigDecimal balance = salary.valueOrZero();
		boolean anyApprox = salary.approx();
		List<GroupTotal> totals = new ArrayList<>();
		for (List<Amount> items : groups) {
			BigDecimal subtotal = Money.ZERO;
			boolean approx = false;
			for (Amount item : items) {
				subtotal = subtotal.add(item.valueOrZero());
				approx |= item.approx();
			}
			balance = balance.subtract(subtotal);
			anyApprox |= approx;
			totals.add(new GroupTotal(Money.round(subtotal), approx, Money.round(balance), anyApprox));
		}
		return new Result(totals, Money.round(balance), anyApprox);
	}
}
