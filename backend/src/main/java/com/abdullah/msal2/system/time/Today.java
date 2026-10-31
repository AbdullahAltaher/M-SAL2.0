package com.abdullah.msal2.system.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Today" in the user's time zone, never the server's (CLAUDE.md rule 14, R-GEN-02).
 * Every "today", "yesterday" and cycle calculation starts from here.
 */
public final class Today {

	private Today() {
	}

	public static LocalDate of(Clock clock, String timeZone) {
		return of(Instant.now(clock), timeZone);
	}

	public static LocalDate of(Instant now, String timeZone) {
		return LocalDate.ofInstant(now, ZoneId.of(timeZone));
	}
}
