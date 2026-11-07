package com.abdullah.msal2.banksms;

import static com.abdullah.msal2.banksms.FakeSms.debit;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.banksms.AdibSmsParser.Debit;

/**
 * Reading ADIB's debit messages (BANK-SMS.md section 2 and 4.4). All messages are made up.
 */
class AdibSmsParserTest {

	private static Debit read(String text) {
		return AdibSmsParser.parse(text).orElseThrow();
	}

	@Test
	void aShortNamePaddedWithSpaces() {
		Debit d = read(debit("12.00", "987", "BLUE FALCON CAFE", "Du bai"));
		assertThat(d.amount()).isEqualByComparingTo("12.00");
		assertThat(d.amount().scale()).isEqualTo(2);
		assertThat(d.suffix()).isEqualTo("987");
		assertThat(d.merchant()).isEqualTo("BLUE FALCON CAFE");
	}

	@Test
	void aNameCutAt22Characters() {
		// the bank cut "HOPE CHARITY FOUNDATION" to its 22 characters
		Debit d = read(debit("1.00", "987", "HOPE CHARITY FOUNDATION", "Du bai"));
		assertThat(d.merchant()).isEqualTo("HOPE CHARITY FOUNDATIO");
	}

	@Test
	void aCityWithASpaceInsideIsNotPartOfTheName() {
		assertThat(read(debit("5.00", "987", "HOPE CHARITY INT", "Sh arjah")).merchant()).isEqualTo("HOPE CHARITY INT");
		assertThat(read(debit("5.00", "987", "HOPE CHARITY INT", "DU BAI")).merchant()).isEqualTo("HOPE CHARITY INT");
	}

	@Test
	void aPhoneNumberInPlaceOfTheCity() {
		Debit d = read(debit("39.99", "987", "FAKESTORE.EXAMPLE", "+10000000000"));
		assertThat(d.merchant()).isEqualTo("FAKESTORE.EXAMPLE");
		assertThat(d.amount()).isEqualByComparingTo("39.99");
	}

	@Test
	void smallLettersAndSpacesInsideTheNameAreKept() {
		Debit d = read(debit("8.50", "987", "Palm   grocery-mart", "Ab u Dhabi"));
		assertThat(d.merchant()).isEqualTo("Palm grocery-mart");
	}

	@Test
	void anAmountWithThousandsSeparators() {
		Debit d = read(debit("1,250.00", "987", "DESERT FURNITURE", "Du bai"));
		assertThat(d.amount()).isEqualTo(new BigDecimal("1250.00"));
	}

	@Test
	void theAccountDigitsAreKeptAsTheyAre() {
		assertThat(read(debit("3.00", "04512", "SAND KIOSK", "Du bai")).suffix()).isEqualTo("04512");
	}

	@Test
	void spacesAroundTheMessageDoNotMatter() {
		assertThat(AdibSmsParser.parse("\n  " + debit("3.00", "SAND KIOSK") + "  \n")).isPresent();
	}

	@Test
	void aDepositIsIgnored() {
		assertThat(AdibSmsParser.parse(FakeSms.deposit("500.00"))).isEmpty();
	}

	@Test
	void aCashWithdrawalOrAnotherBankIsIgnored() {
		assertThat(AdibSmsParser.parse("Cash withdrawal of AED 200.00 from your a/c *987 at ATM 0000 Du bai AE."
				+ " Avl Bal is AED 4121.00")).isEmpty();
		assertThat(AdibSmsParser.parse("Your card ending 1111 was charged USD 5.00 at SAND KIOSK.")).isEmpty();
		assertThat(AdibSmsParser.parse("Your verification code is 123456")).isEmpty();
		assertThat(AdibSmsParser.parse("")).isEmpty();
		assertThat(AdibSmsParser.parse(null)).isEmpty();
	}

	@Test
	void aZeroAmountIsNotAnExpense() {
		assertThat(AdibSmsParser.parse(debit("0.00", "SAND KIOSK"))).isEmpty();
	}
}
