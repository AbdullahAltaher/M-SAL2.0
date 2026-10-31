package com.abdullah.msal2.accounts;

import static com.abdullah.msal2.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.accounts.AccountRules.AccountData;

/**
 * R-ACC (BUSINESS_RULES.md section 5).
 */
class AccountRulesTest {

	private static final AccountData MAIN = new AccountData(id("main"), "مصاريفي", false);
	private static final AccountData HOME = new AccountData(id("home"), "البيت", false);
	private static final AccountData OLD = new AccountData(id("old"), "قديم", true);

	@Test
	void currentIsTheLastUsedWhenActive() {
		assertThat(AccountRules.current(List.of(MAIN, HOME), id("home"))).contains(HOME);
	}

	@Test
	void currentFallsBackToTheFirstActive() {
		assertThat(AccountRules.current(List.of(OLD, MAIN, HOME), id("old"))).as("last used is archived").contains(MAIN);
		assertThat(AccountRules.current(List.of(MAIN, HOME), null)).contains(MAIN);
		assertThat(AccountRules.current(List.of(MAIN, HOME), id("deleted"))).contains(MAIN);
	}

	@Test
	void theLastActiveAccountCannotBeArchived() {
		assertThat(AccountRules.canArchive(List.of(MAIN, OLD), id("main"))).isFalse();
		assertThat(AccountRules.canArchive(List.of(MAIN, HOME, OLD), id("main"))).isTrue();
	}
}
