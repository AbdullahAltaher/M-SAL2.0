package com.abdullah.msal2.system.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * R-GEN-01, with the same results as M-SAL's {@code roundMoney} and {@code sumMoney}.
 */
class MoneyTest {

	private static BigDecimal m(String value) {
		return new BigDecimal(value);
	}

	@Test
	void sumHasNoFloatDrift() {
		// sumMoney([0.1, 0.2]) === 0.3 and ten times 0.1 === 1
		assertThat(Money.sum(List.of(m("0.1"), m("0.2")))).isEqualByComparingTo("0.30");
		assertThat(Money.sum(Collections.nCopies(10, m("0.1")))).isEqualByComparingTo("1.00");
		assertThat(Money.sum(List.of(m("100.10"), m("200.20"), m("0.30")))).isEqualByComparingTo("300.60");
	}

	@Test
	void halvesRoundUpLikeMathRound() {
		assertThat(Money.round(m("0.005"))).isEqualTo(m("0.01"));
		assertThat(Money.round(m("10.456"))).isEqualTo(m("10.46"));
		assertThat(Money.round(m("0.004"))).isEqualTo(m("0.00"));
		// Math.round(-0.5) is -0: negative halves go toward zero
		assertThat(Money.round(m("-1.005"))).isEqualTo(m("-1.00"));
		assertThat(Money.round(m("-1.006"))).isEqualTo(m("-1.01"));
		assertThat(Money.round(m("-0.004"))).isEqualTo(m("0.00"));
	}

	@Test
	void divideRoundsOnce() {
		// 900 / 21 = 42.857142… → 42.86
		assertThat(Money.divide(m("900"), 21)).isEqualTo(m("42.86"));
		assertThat(Money.divide(m("2617.98"), 24)).isEqualTo(m("109.08"));
	}

	@Test
	void cents() {
		assertThat(Money.cents(m("46.25"))).isEqualTo(4625);
		assertThat(Money.cents(m("0.1"))).isEqualTo(10);
	}
}
