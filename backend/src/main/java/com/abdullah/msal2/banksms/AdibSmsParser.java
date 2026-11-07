package com.abdullah.msal2.banksms;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a debit from an ADIB message (BANK-SMS.md section 2 and 4.4). Pure logic, no Spring.
 * <pre>Transaction of AED 1.00 debited from your a/c *123 at SOME SHOP             Du bai AE. Avl Bal is AED 0.00</pre>
 * After {@code at } comes the merchant in a field of 22 characters (padded with spaces, or cut),
 * then the city or a phone number, then the country. The merchant is those 22 characters without
 * the extra spaces. Anything else (a deposit, a cash withdrawal, another bank) is not a debit and
 * is ignored (B-20).
 */
public final class AdibSmsParser {

	/** The bank's fixed width of the merchant field. */
	static final int MERCHANT_WIDTH = 22;

	/** The largest entry amount (R-ENT-03). */
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999");

	private static final Pattern DEBIT = Pattern.compile(
			"^Transaction of AED ([\\d,]+\\.\\d{2}) debited from your a/c \\*(\\d+) at (.+?)\\. Avl Bal",
			Pattern.DOTALL);
	private static final Pattern SPACES = Pattern.compile("\\s+");

	/** A debit: the amount in dirhams, the account's last digits and the merchant's name. */
	public record Debit(BigDecimal amount, String suffix, String merchant) {
	}

	private AdibSmsParser() {
	}

	public static Optional<Debit> parse(String text) {
		if (text == null) {
			return Optional.empty();
		}
		Matcher m = DEBIT.matcher(text.strip());
		if (!m.find()) {
			return Optional.empty();
		}
		BigDecimal amount = new BigDecimal(m.group(1).replace(",", ""));
		String merchant = merchant(m.group(3));
		if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0 || merchant.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new Debit(amount, m.group(2), merchant));
	}

	/** The first 22 characters, with runs of spaces made one and none at the ends. */
	static String merchant(String afterAt) {
		String field = afterAt.length() > MERCHANT_WIDTH ? afterAt.substring(0, MERCHANT_WIDTH) : afterAt;
		return SPACES.matcher(field).replaceAll(" ").strip();
	}
}
