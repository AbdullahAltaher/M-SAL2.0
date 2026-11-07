package com.abdullah.msal2.banksms;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.accounts.Account;
import com.abdullah.msal2.accounts.AccountRepository;
import com.abdullah.msal2.banksms.AdibSmsParser.Debit;
import com.abdullah.msal2.banksms.BankSmsDtos.IngestResponse;
import com.abdullah.msal2.banksms.BankSmsDtos.IngestStatus;
import com.abdullah.msal2.banksms.CategorySuggester.Suggestion;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.time.Today;

/**
 * A message from the shortcut becomes an entry waiting for review, never an entry (B-01,
 * BANK-SMS.md 4.3). The answer is a short Arabic line the shortcut shows as a notification (B-08).
 */
@Service
public class SmsIngestService {

	static final String NOT_A_DEBIT = "ليست رسالة خصم";
	static final String NOT_LINKED = "حساب غير مربوط";
	static final String ARCHIVED = "الحساب المربوط مؤرشف";
	static final String DUPLICATE = "وصلت من قبل";
	static final String PICK_CATEGORY = "اختر الفئة";

	private final AccountRepository accounts;
	private final SmsLinks links;
	private final PendingEntries pending;
	private final CategorySuggester suggester;
	private final AuditService audit;
	private final Clock clock;

	public SmsIngestService(AccountRepository accounts, SmsLinks links, PendingEntries pending,
			CategorySuggester suggester, AuditService audit, Clock clock) {
		this.accounts = accounts;
		this.links = links;
		this.pending = pending;
		this.suggester = suggester;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public IngestResponse ingest(CurrentUser user, String text) {
		Optional<Debit> parsed = AdibSmsParser.parse(text);
		if (parsed.isEmpty()) {
			return new IngestResponse(IngestStatus.IGNORED, NOT_A_DEBIT);
		}
		Debit debit = parsed.get();
		// The shortcut already filters by account; the server checks again (B-11)
		Optional<Account> account = links.find(user.id(), debit.suffix())
				.flatMap(l -> accounts.findOwned(l.accountId(), user.id()));
		if (account.isEmpty()) {
			return new IngestResponse(IngestStatus.IGNORED, NOT_LINKED);
		}
		if (account.get().isArchived()) {
			return new IngestResponse(IngestStatus.IGNORED, ARCHIVED);
		}

		UUID budgetId = account.get().getBudgetId();
		Suggestion suggestion = suggester.forBudget(budgetId).apply(debit.merchant());
		Instant now = Instant.now(clock);
		PendingEntries.Row row = new PendingEntries.Row(UUID.randomUUID(), user.id(), budgetId, account.get().getId(),
				debit.amount(), debit.merchant(), suggestion.category().map(c -> c.getId()).orElse(null), now,
				entryDate(now, user.timeZone()), text, SmsTokens.hash(text.strip()));
		if (!pending.insert(row)) {
			return new IngestResponse(IngestStatus.DUPLICATE, DUPLICATE);
		}
		audit.record(user.id(), AuditAction.CREATE, "pending_entry", row.id(), null, row.withoutText());
		String category = suggestion.category().map(c -> c.getName()).orElse(PICK_CATEGORY);
		return new IngestResponse(IngestStatus.PENDING, "طلب جديد: " + amount(debit.amount()) + " د.إ · " + category);
	}

	/** The message has no date: it is the day it arrived, in the user's time zone (rule 14). */
	static LocalDate entryDate(Instant receivedAt, String timeZone) {
		return Today.of(receivedAt, timeZone);
	}

	/** {@code 1250.5 → "1,250.50"}. */
	static String amount(BigDecimal value) {
		return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
	}
}
