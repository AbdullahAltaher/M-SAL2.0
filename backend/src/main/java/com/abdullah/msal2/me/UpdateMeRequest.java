package com.abdullah.msal2.me;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /me}. A missing field stays unchanged; an empty {@code displayName} clears it.
 * {@code dailyReminderTime} is added in phase 7 with its column.
 *
 * @param version the version the client has (optimistic locking, API.md section 1)
 */
public record UpdateMeRequest(
		@Size(max = 100) String displayName,
		String timeZone,
		UUID lastBudgetId,
		@NotNull Integer version) {
}
