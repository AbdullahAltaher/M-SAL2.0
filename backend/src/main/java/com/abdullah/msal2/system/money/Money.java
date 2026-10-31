package com.abdullah.msal2.system.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Money arithmetic (R-GEN-01). Amounts are {@link BigDecimal} with 2 decimals, never
 * {@code double} (CLAUDE.md rule 7).
 */
public final class Money {

	public static final BigDecimal ZERO = new BigDecimal("0.00");

	/** Scale for intermediate division results, enough for any exact quotient of a day count. */
	private static final int DIVISION_SCALE = 12;

	private Money() {
	}

	/**
	 * Rounds to 2 decimals the way M-SAL's {@code roundMoney} does ({@code Math.round}):
	 * halves go up toward positive infinity, so {@code 0.005 → 0.01} and {@code -0.005 → -0.00}.
	 */
	public static BigDecimal round(BigDecimal value) {
		RoundingMode mode = value.signum() < 0 ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP;
		BigDecimal rounded = value.setScale(2, mode);
		return rounded.signum() == 0 ? ZERO : rounded;
	}

	/** Exact sum, 2 decimals ({@code sumMoney}). */
	public static BigDecimal sum(Collection<BigDecimal> values) {
		BigDecimal total = ZERO;
		for (BigDecimal value : values) {
			total = total.add(value);
		}
		return round(total);
	}

	/** {@code value / divisor}, rounded to money. */
	public static BigDecimal divide(BigDecimal value, long divisor) {
		return round(value.divide(BigDecimal.valueOf(divisor), DIVISION_SCALE, RoundingMode.HALF_EVEN));
	}

	/** {@code value / divisor} unrounded, for a later sum before rounding. */
	public static BigDecimal divideExact(BigDecimal value, long divisor) {
		return value.divide(BigDecimal.valueOf(divisor), DIVISION_SCALE, RoundingMode.HALF_EVEN);
	}

	/** The amount in cents ({@code 46.25 → 4625}). */
	public static long cents(BigDecimal value) {
		return round(value).movePointRight(2).longValueExact();
	}

	/** Parses a plain decimal string, such as in tests ({@code "46.25"}). */
	public static BigDecimal of(String value) {
		return round(new BigDecimal(value));
	}
}
