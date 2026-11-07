package com.abdullah.msal2.expenses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.abdullah.msal2.cycles.CycleSummary;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SRV-001 requests and responses (API.md section 4). The amount, date and type arrive as
 * strings so each wrong value gets its own error code (R-ENT-02).
 */
public final class EntryDtos {

	private EntryDtos() {
	}

	/**
	 * @param cycleKey only after create and duplicate: the cycle the entry falls in, so the
	 *                 screen can jump there
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EntryResponse(UUID id, EntryType type, BigDecimal amount, String name, UUID categoryId,
			LocalDate entryDate, UUID accountId, Instant createdAt, Instant updatedAt, Instant deletedAt, Integer version,
			String cycleKey) {

		public static EntryResponse of(Entry e) {
			return of(e, null);
		}

		public static EntryResponse of(Entry e, String cycleKey) {
			return new EntryResponse(e.getId(), e.getType(), e.getAmount(), e.getName(), e.getCategoryId(),
					e.getEntryDate(), e.getAccountId(), e.getCreatedAt(), e.getUpdatedAt(), e.getDeletedAt(), e.getVersion(),
					cycleKey);
		}
	}

	public record CreateEntryRequest(String type, String amount, @Size(max = 200) String name, UUID categoryId,
			String entryDate, UUID accountId) {
	}

	public record UpdateEntryRequest(String type, String amount, @Size(max = 200) String name, UUID categoryId,
			String entryDate, UUID accountId, @NotNull Integer version) {
	}

	public record AccountRef(UUID id, String name) {
	}

	public record CycleRef(UUID id, LocalDate startDate, LocalDate expectedNextSalaryDate, LocalDate lastDay,
			boolean isOpen) {
	}

	/**
	 * @param summary      null for "no cycle"
	 * @param noCycleTotal the expenses of "no cycle", else null
	 */
	public record Selected(String key, CycleRef cycle, CycleSummary summary, BigDecimal noCycleTotal) {
	}

	/**
	 * Everything the home screen needs in one call (R-CYC-04 to R-CYC-06).
	 *
	 * @param pendingCount the budget's bank messages waiting for review, for the bar above the
	 *                     account card (BANK-SMS.md B-04); they are not entries (B-06)
	 */
	public record HomeResponse(LocalDate today, AccountRef account, List<AccountRef> activeAccounts,
			List<String> cycleKeys, Selected selected, List<EntryResponse> recent, int pendingCount) {
	}

	public record DayResponse(LocalDate date, BigDecimal spent, BigDecimal extra, List<EntryResponse> entries) {
	}

	public record DaysResponse(List<DayResponse> days) {
	}

	public record PurgeResponse(int purged) {
	}

	public record SuggestionsResponse(List<String> suggestions) {
	}

	/** The entry sheet's one-tap shortcuts and category order (R-ENT-14, R-ENT-15). */
	public record QuickResponse(List<QuickPicks.Shortcut> shortcuts, List<UUID> categoryOrder) {
	}
}
