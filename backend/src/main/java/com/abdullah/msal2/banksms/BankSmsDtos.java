package com.abdullah.msal2.banksms;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SRV-011 requests and responses (BANK-SMS.md 4.3 and 4.5, API.md SRV-011).
 */
public final class BankSmsDtos {

	private BankSmsDtos() {
	}

	// ── The shortcut ────────────────────────────────────────────

	/** The message as the iPhone received it. */
	public record IngestRequest(@NotNull @Size(max = IngestLimits.MAX_TEXT) String text) {
	}

	public enum IngestStatus {
		PENDING, DUPLICATE, IGNORED
	}

	/** Always 200: the shortcut shows {@code message} as a notification (B-08). */
	public record IngestResponse(IngestStatus status, String message) {
	}

	public static final class IngestLimits {

		/** The body limit of the ingest endpoint (BANK-SMS.md 4.3). */
		public static final int MAX_BODY = 2048;
		public static final int MAX_TEXT = 1500;

		private IngestLimits() {
		}
	}

	// ── Review ──────────────────────────────────────────────────

	/**
	 * @param categoryId   the suggested category, or null when the user must pick one
	 * @param askEachTime  the merchant is one whose category is picked every time (B-15)
	 * @param smsText      the bank's message exactly as it came (B-03)
	 */
	public record PendingEntryResponse(UUID id, UUID accountId, BigDecimal amount, String merchant, UUID categoryId,
			boolean askEachTime, Instant receivedAt, LocalDate entryDate, String smsText) {
	}

	/** @param remember add the merchant's name as a word of this category (B-16) */
	public record ApproveRequest(UUID categoryId, @Size(max = 200) String name, Boolean remember) {
	}

	// ── Settings ────────────────────────────────────────────────

	public record LinkResponse(String suffix, UUID accountId, String accountName, boolean archived, UUID budgetId,
			String budgetName) {
	}

	/** The active token without the token itself: its last 4 characters and when it was last used. */
	public record TokenStatus(String hint, Instant createdAt, Instant lastUsedAt) {
	}

	/** @param categoryId null = "اختر الفئة كل مرة" (B-15) */
	public record KeywordResponse(UUID id, UUID budgetId, String keyword, UUID categoryId) {
	}

	/** @param token null when there is no active token */
	public record SettingsResponse(List<LinkResponse> links, TokenStatus token, List<KeywordResponse> keywords) {
	}

	public record LinkRequest(@NotNull UUID accountId) {
	}

	/** Shown once, then only its hint (B-19). */
	public record NewTokenResponse(String token, String hint, Instant createdAt) {
	}

	public record KeywordRequest(@Size(max = 200) String keyword, UUID categoryId) {
	}
}
