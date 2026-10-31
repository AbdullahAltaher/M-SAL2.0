package com.abdullah.msal2.me;

import java.time.Duration;
import java.time.Instant;

/**
 * The reminder to export a backup (R-IO-01), as in M-SAL's {@code exportReminder}:
 * due when the last export, or the first use if there was none, is 14 or more days ago,
 * and there is data to lose.
 *
 * @param due   show the reminder
 * @param days  whole days since the last export (or the first use)
 * @param never no export yet
 */
public record ExportReminder(boolean due, long days, boolean never) {

	static final int REMIND_AFTER_DAYS = 14;

	/**
	 * @param lastExportAt the last export, or {@code null} if never
	 * @param firstUseAt   when the user started (M-SAL's {@code firstSeen})
	 */
	public static ExportReminder of(Instant lastExportAt, Instant firstUseAt, Instant now, boolean hasData) {
		Instant base = lastExportAt != null ? lastExportAt : firstUseAt;
		long days = Math.floorDiv(Duration.between(base, now).toMillis(), Duration.ofDays(1).toMillis());
		return new ExportReminder(hasData && days >= REMIND_AFTER_DAYS, days, lastExportAt == null);
	}
}
