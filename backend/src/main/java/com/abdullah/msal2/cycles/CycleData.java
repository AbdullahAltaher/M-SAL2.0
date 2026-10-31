package com.abdullah.msal2.cycles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A salary cycle as the calculations see it. Its end is never stored: it ends where the next
 * cycle of the same account starts (R-CYC-01).
 *
 * @param expectedNextSalaryDate optional; needed for "can spend today" (R-CYC-04)
 */
public record CycleData(UUID id, LocalDate startDate, BigDecimal allowance, LocalDate expectedNextSalaryDate) {

	public CycleData withStartDate(LocalDate date) {
		return new CycleData(id, date, allowance, expectedNextSalaryDate);
	}
}
