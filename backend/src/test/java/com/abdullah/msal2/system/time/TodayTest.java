package com.abdullah.msal2.system.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * R-GEN-02 and CLAUDE.md rule 14: the "UTC+4 midnight bug" tests from M-SAL's cycles.test.js.
 */
class TodayTest {

	@Test
	void justAfterMidnightInDubaiIsTheNewDay() {
		// 00:30 on 2 Oct in Dubai is still 1 Oct in UTC
		Instant justAfter = Instant.parse("2026-10-01T20:30:00Z");
		assertThat(Today.of(justAfter, "Asia/Dubai")).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(Today.of(justAfter, "UTC")).isEqualTo(LocalDate.of(2026, 10, 1));
	}

	@Test
	void eveningInLosAngelesIsStillToday() {
		// 20:00 on 2 Oct in Los Angeles is already 3 Oct in UTC
		Instant evening = Instant.parse("2026-10-03T03:00:00Z");
		assertThat(Today.of(evening, "America/Los_Angeles")).isEqualTo(LocalDate.of(2026, 10, 2));
	}

	@Test
	void edgesOfTheDayAndYear() {
		assertThat(Today.of(Instant.parse("2026-10-01T20:00:00Z"), "Asia/Dubai")).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(Today.of(Instant.parse("2026-10-02T19:59:59Z"), "Asia/Dubai")).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(Today.of(Instant.parse("2025-12-31T20:05:00Z"), "Asia/Dubai")).isEqualTo(LocalDate.of(2026, 1, 1));
	}
}
