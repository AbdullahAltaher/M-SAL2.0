package com.abdullah.msal2.cycles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.accounts.Account;
import com.abdullah.msal2.accounts.AccountService;
import com.abdullah.msal2.expenses.Entry;
import com.abdullah.msal2.expenses.EntryRepository;

/**
 * Loads a {@link Ledger}: the user's account, its cycles and its live entries.
 */
@Service
public class LedgerService {

	private final AccountService accounts;
	private final SalaryCycleRepository cycles;
	private final EntryRepository entries;

	public LedgerService(AccountService accounts, SalaryCycleRepository cycles, EntryRepository entries) {
		this.accounts = accounts;
		this.cycles = cycles;
		this.entries = entries;
	}

	/** The user's account and its data, else {@code SYS-E004}. */
	@Transactional(readOnly = true)
	public Ledger load(UUID userId, UUID accountId) {
		Account account = accounts.owned(userId, accountId);
		Map<UUID, Entry> entities = new LinkedHashMap<>();
		entries.findByAccountIdAndDeletedAtIsNull(accountId).forEach(e -> entities.put(e.getId(), e));
		return new Ledger(account, cycles.findByAccountId(accountId).stream().map(SalaryCycle::toData).toList(),
				entities.values().stream().map(Entry::toData).toList(), entities);
	}
}
