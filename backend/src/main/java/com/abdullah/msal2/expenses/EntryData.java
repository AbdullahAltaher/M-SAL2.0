package com.abdullah.msal2.expenses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An entry as the calculations see it (R-ENT-01). Built from the database in the services;
 * the calculation classes never touch the database.
 *
 * @param deletedAt set for a soft-deleted entry, which counts nowhere (R-GEN-03)
 */
public record EntryData(
		UUID id,
		EntryType type,
		BigDecimal amount,
		String name,
		UUID categoryId,
		LocalDate date,
		UUID accountId,
		Instant createdAt,
		Instant deletedAt) {

	public boolean isIncome() {
		return type == EntryType.INCOME;
	}

	public boolean isExpense() {
		return type != EntryType.INCOME;
	}

	public boolean isLive() {
		return deletedAt == null;
	}
}
