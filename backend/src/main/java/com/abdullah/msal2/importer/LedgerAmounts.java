package com.abdullah.msal2.importer;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.abdullah.msal2.importer.ImportFile.Amount;
import com.abdullah.msal2.system.money.Money;

/**
 * A monthly budget amount as M-SAL keeps it, a raw text such as {@code "~15,000"} (R-BUD-03,
 * M-SAL's parseAmount), read the way Angular's parseLedgerAmount reads it: a leading "~" means
 * approximate, Arabic digits are converted, separators and spaces are dropped, the leading
 * number counts like JavaScript's parseFloat, and text that is not a number counts as 0. An
 * empty field stays empty.
 */
public final class LedgerAmounts {

	private static final Pattern LEADING_NUMBER = Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

	/** numeric(12,2) holds at most this */
	private static final BigDecimal MAX = new BigDecimal("9999999999.99");

	public static final Amount EMPTY = new Amount(null, false);

	private LedgerAmounts() {
	}

	/** null when the amount is too large to keep. */
	public static Amount parse(String raw) {
		if (raw == null) {
			return EMPTY;
		}
		String s = latinDigits(raw).replace('٫', '.').replace("٬", "").strip();
		if (s.isEmpty()) {
			return EMPTY;
		}
		boolean approx = s.charAt(0) == '~' || s.charAt(0) == '～' || s.charAt(0) == '∼';
		if (approx) {
			s = s.substring(1).strip();
		}
		s = s.replaceAll("[,\\s]", "");
		Matcher m = LEADING_NUMBER.matcher(s);
		if (!m.find()) {
			return new Amount(Money.ZERO, approx);
		}
		BigDecimal value = Money.round(new BigDecimal(m.group()));
		return value.abs().compareTo(MAX) > 0 ? null : new Amount(value, approx);
	}

	/** The raw text M-SAL shows for an amount ({@code "~15000"}, {@code "-20.5"}, {@code ""}). */
	public static String raw(BigDecimal value, boolean approx) {
		if (value == null) {
			return "";
		}
		String number = value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
		return approx ? "~" + number : number;
	}

	/** Arabic-Indic and Persian digits to 0-9 (R-GEN-04). */
	public static String latinDigits(String s) {
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= '٠' && c <= '٩') {
				out.append((char) ('0' + c - '٠'));
			}
			else if (c >= '۰' && c <= '۹') {
				out.append((char) ('0' + c - '۰'));
			}
			else {
				out.append(c);
			}
		}
		return out.toString();
	}
}
