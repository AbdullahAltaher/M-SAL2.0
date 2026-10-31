package com.abdullah.msal2.system.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * Parsing of the unified input formats (API.md section 1). Angular turns what the user types
 * (Arabic digits, separators) into these; the server only checks them.
 */
public final class Inputs {

	/** An entry or cycle amount: {@code "46.25"}, never negative. */
	public static final Pattern AMOUNT = Pattern.compile("^\\d{1,9}(\\.\\d{1,2})?$");

	/** A monthly budget amount: may be negative. */
	public static final Pattern BUDGET_AMOUNT = Pattern.compile("^-?\\d{1,10}(\\.\\d{1,2})?$");

	private static final BigDecimal MAX_ENTRY = new BigDecimal("999999999");

	private Inputs() {
	}

	/** A strict {@code YYYY-MM-DD} date ({@code 2026-02-30} is refused), else {@code error}. */
	public static LocalDate date(String value, ErrorCode error) {
		if (value == null) {
			throw new ApiException(error);
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException e) {
			throw new ApiException(error);
		}
	}

	/** An entry amount: above 0 and at most 999,999,999 (R-ENT-03), else {@code SRV-001-E001}. */
	public static BigDecimal entryAmount(String value) {
		if (value == null || !AMOUNT.matcher(value).matches()) {
			throw new ApiException(ErrorCode.SRV_001_E001);
		}
		BigDecimal amount = new BigDecimal(value).setScale(2);
		if (amount.signum() <= 0 || amount.compareTo(MAX_ENTRY) > 0) {
			throw new ApiException(ErrorCode.SRV_001_E001);
		}
		return amount;
	}

	/** A cycle allowance: 0 or more (R-CYC-03), else {@code SRV-003-E002}. */
	public static BigDecimal allowance(String value) {
		if (value == null || !AMOUNT.matcher(value).matches()) {
			throw new ApiException(ErrorCode.SRV_003_E002);
		}
		return new BigDecimal(value).setScale(2);
	}

	/** A monthly budget amount, or null for an empty field (R-BUD-03), else {@code SRV-002-E003}. */
	public static BigDecimal budgetAmount(String value) {
		if (value == null) {
			return null;
		}
		if (!BUDGET_AMOUNT.matcher(value).matches()) {
			throw new ApiException(ErrorCode.SRV_002_E003);
		}
		return new BigDecimal(value).setScale(2);
	}
}
