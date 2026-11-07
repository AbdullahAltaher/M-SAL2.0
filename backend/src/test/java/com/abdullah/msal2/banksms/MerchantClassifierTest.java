package com.abdullah.msal2.banksms;

import static com.abdullah.msal2.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.banksms.MerchantClassifier.Keyword;

/**
 * Suggesting a category from the user's words (BANK-SMS.md 4.4, B-13 to B-15). Made-up merchants.
 */
class MerchantClassifierTest {

	private static final UUID FOOD = id("cat_food");
	private static final UUID COFFEE = id("cat_coffee");
	private static final UUID FUEL = id("cat_fuel");
	private static final UUID CHARITY = id("cat_charity");

	private static Optional<UUID> categoryOf(String merchant, Keyword... keywords) {
		return MerchantClassifier.match(merchant, List.of(keywords)).map(Keyword::categoryId);
	}

	@Test
	void cleaningMakesCapitalsAndTurnsSignsIntoSpaces() {
		assertThat(MerchantClassifier.clean("Al-Sarab  cafeteria.")).isEqualTo("AL SARAB CAFETERIA");
		assertThat(MerchantClassifier.clean("ONLINE/SHOP.EXAMPLE")).isEqualTo("ONLINE SHOP EXAMPLE");
		assertThat(MerchantClassifier.clean("  ")).isEmpty();
		assertThat(MerchantClassifier.clean(null)).isEmpty();
	}

	@Test
	void cafeDoesNotMatchCafeteria() {
		Keyword cafe = new Keyword("CAFE", COFFEE);
		assertThat(categoryOf("BLUE FALCON CAFETERIA", cafe)).isEmpty();
		assertThat(categoryOf("BLUE FALCON CAFE", cafe)).contains(COFFEE);
	}

	@Test
	void capitalsAndSmallLettersAreTheSame() {
		assertThat(categoryOf("blue falcon cafe", new Keyword("Cafe", COFFEE))).contains(COFFEE);
	}

	@Test
	void signsCountAsSpaces() {
		assertThat(categoryOf("CAFE.BLUE-FALCON", new Keyword("CAFE", COFFEE))).contains(COFFEE);
	}

	@Test
	void theWordEarliestInTheNameWins() {
		// a restaurant inside a petrol station: the shop's name comes before its place
		Keyword grill = new Keyword("GRILL", FOOD);
		Keyword petrol = new Keyword("PETROL", FUEL);
		assertThat(categoryOf("FALCON GRILL-SAND PETROL", petrol, grill)).contains(FOOD);
		assertThat(categoryOf("SAND PETROL-FALCON GRILL", grill, petrol)).contains(FUEL);
	}

	@Test
	void atTheSamePlaceTheLongerWordWins() {
		Keyword red = new Keyword("RED", FOOD);
		Keyword crescent = new Keyword("RED CRESCENT", CHARITY);
		assertThat(categoryOf("RED CRESCENT FUND", red, crescent)).contains(CHARITY);
	}

	@Test
	void aWordOfSeveralWordsMatchesThemInOrder() {
		Keyword hope = new Keyword("HOPE CHARITY", CHARITY);
		assertThat(categoryOf("THE HOPE CHARITY INT", hope)).contains(CHARITY);
		assertThat(categoryOf("CHARITY OF HOPE", hope)).isEmpty();
		assertThat(categoryOf("HOPE", hope)).isEmpty();
	}

	@Test
	void aWordWithoutCategoryMeansPickEveryTime() {
		Keyword delivery = new Keyword("FASTBITE", null);
		Optional<Keyword> match = MerchantClassifier.match("FASTBITE DELIVERY", List.of(delivery,
				new Keyword("DELIVERY", FOOD)));
		assertThat(match).isPresent();
		assertThat(match.get().categoryId()).isNull();
	}

	@Test
	void anUnknownMerchantHasNoSuggestion() {
		assertThat(categoryOf("SAND KIOSK", new Keyword("CAFE", COFFEE))).isEmpty();
		assertThat(categoryOf("SAND KIOSK")).isEmpty();
	}
}
