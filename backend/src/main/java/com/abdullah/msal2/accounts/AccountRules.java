package com.abdullah.msal2.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account rules (R-ACC in BUSINESS_RULES.md section 5).
 */
public final class AccountRules {

	private AccountRules() {
	}

	/** An account as these rules see it. */
	public record AccountData(UUID id, String name, boolean archived) {
	}

	/**
	 * The current account: the last used one if it is still active, else the first active one
	 * ({@code curAccount}).
	 *
	 * @param accounts the budget's accounts in display order
	 */
	public static Optional<AccountData> current(List<AccountData> accounts, UUID lastAccountId) {
		List<AccountData> active = accounts.stream().filter(a -> !a.archived()).toList();
		return active.stream().filter(a -> a.id().equals(lastAccountId)).findFirst()
				.or(() -> active.stream().findFirst());
	}

	/** The last active account cannot be archived. */
	public static boolean canArchive(List<AccountData> accounts, UUID accountId) {
		return accounts.stream().anyMatch(a -> !a.archived() && !a.id().equals(accountId));
	}
}
