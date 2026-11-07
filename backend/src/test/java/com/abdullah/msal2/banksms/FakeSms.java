package com.abdullah.msal2.banksms;

/**
 * Made-up bank messages in ADIB's format (BANK-SMS.md section 2). The repository is public:
 * every merchant, account number and balance here is invented (BANK-SMS.md 4.7).
 */
public final class FakeSms {

	public static final String SUFFIX = "987";

	private FakeSms() {
	}

	/**
	 * A debit: the merchant padded with spaces (or cut) to the bank's 22 characters, then the
	 * city (or a phone number) and the country.
	 */
	public static String debit(String amount, String suffix, String merchant, String city) {
		String field = merchant.length() >= 22 ? merchant.substring(0, 22) : String.format("%-22s", merchant);
		return "Transaction of AED " + amount + " debited from your a/c *" + suffix + " at " + field + " " + city
				+ " AE. Avl Bal is AED 4321.00";
	}

	public static String debit(String amount, String merchant) {
		return debit(amount, SUFFIX, merchant, "Du bai");
	}

	/** A message that is not a debit (B-20). */
	public static String deposit(String amount) {
		return "Dear Customer, AED " + amount + " has been credited to your a/c *" + SUFFIX
				+ ". Avl Bal is AED 4321.00";
	}
}
