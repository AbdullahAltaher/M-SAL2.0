package com.abdullah.msal2.banksms;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.accounts.AccountRepository;
import com.abdullah.msal2.banksms.BankSmsDtos.KeywordRequest;
import com.abdullah.msal2.banksms.BankSmsDtos.KeywordResponse;
import com.abdullah.msal2.banksms.BankSmsDtos.LinkResponse;
import com.abdullah.msal2.banksms.BankSmsDtos.NewTokenResponse;
import com.abdullah.msal2.banksms.BankSmsDtos.SettingsResponse;
import com.abdullah.msal2.banksms.BankSmsDtos.TokenStatus;
import com.abdullah.msal2.budget.BudgetService;
import com.abdullah.msal2.categories.CategoryService;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * "الإعدادات ← رسائل البنك" (B-19): the linked accounts, the shortcut's token and the words by
 * category. Another user's account, budget or category is not found (404, rule 6).
 */
@Service
public class SmsSettingsService {

	static final int MAX_KEYWORD = 40;
	private static final Pattern SUFFIX = Pattern.compile("^[0-9]{3,6}$");

	private final SmsLinks links;
	private final SmsTokens tokens;
	private final MerchantKeywords keywords;
	private final AccountRepository accounts;
	private final BudgetService budgets;
	private final CategoryService categories;
	private final AuditService audit;
	private final Clock clock;

	public SmsSettingsService(SmsLinks links, SmsTokens tokens, MerchantKeywords keywords, AccountRepository accounts,
			BudgetService budgets, CategoryService categories, AuditService audit, Clock clock) {
		this.links = links;
		this.tokens = tokens;
		this.keywords = keywords;
		this.accounts = accounts;
		this.budgets = budgets;
		this.categories = categories;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public SettingsResponse settings(UUID userId) {
		return new SettingsResponse(
				links.of(userId).stream().map(l -> new LinkResponse(l.suffix(), l.accountId(), l.accountName(),
						l.archived(), l.budgetId(), l.budgetName())).toList(),
				tokens.active(userId).map(t -> new TokenStatus(t.hint(), t.createdAt(), t.lastUsedAt())).orElse(null),
				keywords.ofUser(userId).stream().map(SmsSettingsService::toResponse).toList());
	}

	// ── Accounts ────────────────────────────────────────────────

	@Transactional
	public void link(UUID userId, String suffix, UUID accountId) {
		checkSuffix(suffix);
		accounts.findOwned(accountId, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
		links.save(userId, suffix, accountId, Instant.now(clock));
		audit.record(userId, AuditAction.UPDATE, "sms_account_link", null, null,
				Map.of("suffix", suffix, "accountId", accountId));
	}

	@Transactional
	public void unlink(UUID userId, String suffix) {
		checkSuffix(suffix);
		LinkResponse before = links.find(userId, suffix)
				.map(l -> new LinkResponse(l.suffix(), l.accountId(), l.accountName(), l.archived(), l.budgetId(),
						l.budgetName()))
				.orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
		links.delete(userId, suffix);
		audit.record(userId, AuditAction.PURGE, "sms_account_link", null, before, null);
	}

	// ── The token ───────────────────────────────────────────────

	/** A new token, returned this once only; the old one stops working. */
	@Transactional
	public NewTokenResponse newToken(UUID userId) {
		Instant now = Instant.now(clock);
		String token = tokens.replace(userId, now);
		audit.record(userId, AuditAction.CREATE, "sms_token", null, null, Map.of("hint", SmsTokens.hint(token)));
		return new NewTokenResponse(token, SmsTokens.hint(token), now);
	}

	@Transactional
	public void revokeToken(UUID userId) {
		if (tokens.revoke(userId, Instant.now(clock)) > 0) {
			audit.record(userId, AuditAction.DELETE, "sms_token", null, null, null);
		}
	}

	// ── Words ───────────────────────────────────────────────────

	/** Adds a word, or moves it to this category. {@code categoryId} null = "اختر كل مرة" (B-15). */
	@Transactional
	public KeywordResponse addKeyword(UUID userId, UUID budgetId, KeywordRequest request) {
		budgets.owned(userId, budgetId);
		if (request.categoryId() != null) {
			categories.find(userId, request.categoryId())
					.filter(c -> c.getBudgetId().equals(budgetId))
					.orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
		}
		String keyword = MerchantClassifier.clean(request.keyword());
		if (keyword.isEmpty()) {
			throw new ApiException(ErrorCode.SRV_011_E002);
		}
		if (keyword.length() > MAX_KEYWORD) {
			throw new ApiException(ErrorCode.SRV_011_E003);
		}
		KeywordResponse saved = toResponse(
				keywords.save(userId, budgetId, keyword, request.categoryId(), Instant.now(clock)));
		audit.record(userId, AuditAction.UPDATE, "merchant_keyword", saved.id(), null, saved);
		return saved;
	}

	@Transactional
	public void deleteKeyword(UUID userId, UUID id) {
		MerchantKeywords.Row row = keywords.owned(id, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
		keywords.delete(id);
		audit.record(userId, AuditAction.PURGE, "merchant_keyword", id, toResponse(row), null);
	}

	private static void checkSuffix(String suffix) {
		if (suffix == null || !SUFFIX.matcher(suffix).matches()) {
			throw new ApiException(ErrorCode.SRV_011_E001);
		}
	}

	private static KeywordResponse toResponse(MerchantKeywords.Row row) {
		return new KeywordResponse(row.id(), row.budgetId(), row.keyword(), row.categoryId());
	}
}
