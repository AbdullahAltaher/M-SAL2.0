package com.abdullah.msal2.me;

import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /me}. A missing field stays unchanged; an empty {@code displayName} clears it,
 * and {@code dailyReminderTime: null} turns the daily reminder off.
 *
 * @param version the version the client has (optimistic locking, API.md section 1)
 */
public record UpdateMeRequest(
		@Size(max = 100) String displayName,
		String timeZone,
		UUID lastBudgetId,
		LocalTime dailyReminderTime,
		@NotNull Integer version) {
}
