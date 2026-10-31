package com.abdullah.msal2.me;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * R-IO-01, same results as M-SAL's {@code exportReminder}.
 */
class ExportReminderTest {

	private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

	@Test
	void dueAfterFourteenDaysSinceLastExport() {
		ExportReminder reminder = ExportReminder.of(NOW.minus(Duration.ofDays(14)), NOW.minus(Duration.ofDays(90)),
				NOW, true);
		assertThat(reminder).isEqualTo(new ExportReminder(true, 14, false));
	}

	@Test
	void notDueBeforeFourteenFullDays() {
		ExportReminder reminder = ExportReminder.of(NOW.minus(Duration.ofDays(14)).plusSeconds(1), null, NOW, true);
		assertThat(reminder).isEqualTo(new ExportReminder(false, 13, false));
	}

	@Test
	void neverExportedCountsFromFirstUse() {
		ExportReminder reminder = ExportReminder.of(null, NOW.minus(Duration.ofDays(20)), NOW, true);
		assertThat(reminder).isEqualTo(new ExportReminder(true, 20, true));
	}

	@Test
	void notDueWithoutData() {
		ExportReminder reminder = ExportReminder.of(null, NOW.minus(Duration.ofDays(100)), NOW, false);
		assertThat(reminder.due()).isFalse();
	}
}
