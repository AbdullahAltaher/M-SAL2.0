package com.abdullah.msal2.budget;

import java.time.LocalDate;
import java.util.List;

/**
 * The month name a new budget or "new month" uses (R-BUD-05, R-BUD-06), from M-SAL's
 * {@code defaultMonthLabel}: "نوفمبر 2026". The date is today in the user's time zone.
 */
public final class MonthLabel {

	static final List<String> AR_MONTHS = List.of("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو",
			"أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر");

	private MonthLabel() {
	}

	public static String of(LocalDate today) {
		return AR_MONTHS.get(today.getMonthValue() - 1) + " " + today.getYear();
	}
}
