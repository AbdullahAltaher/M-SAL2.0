package com.abdullah.msal2.me;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /me} (API.md SRV-000). {@code services} holds only enabled services the user may use.
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
		int version) {

	public record Rights(boolean read, boolean write) {
	}
}
