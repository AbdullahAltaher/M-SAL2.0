package com.abdullah.msal2.cycles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.abdullah.msal2.accounts.Account;
import com.abdullah.msal2.expenses.Entry;
import com.abdullah.msal2.expenses.EntryData;

/**
 * One account with its cycles and live entries: what the cycle, home, stats and receipt
 * calculations work on.
 *
 * @param entities the live entries as entities, by id, for responses that need their version
 */
public record Ledger(Account account, List<CycleData> cycles, List<EntryData> entries, Map<UUID, Entry> entities) {
}
