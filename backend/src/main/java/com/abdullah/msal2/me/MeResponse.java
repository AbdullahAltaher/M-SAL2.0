package com.abdullah.msal2.me;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /me} (API.md SRV-000). {@code services} holds only enabled services the user may use.
 * {@code dailyReminderTime} is "HH:mm:ss" or null (off); {@code pushPublicKey} is the server's VAPID
 * key for subscribing, or null when this server sends no notifications.
 */
public record MeResponse(
		UUID id,
		String email,
		String displayName,
		String timeZone,
		LocalDate today,
		List<String> roles,
		Map<String, Rights> services,
		UUID lastBudgetId,
		ExportReminder exportReminder,
		LocalTime dailyReminderTime,
		String pushPublicKey,
		int version) {

	public record Rights(boolean read, boolean write) {
	}
}
