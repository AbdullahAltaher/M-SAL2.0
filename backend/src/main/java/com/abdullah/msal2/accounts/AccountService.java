package com.abdullah.msal2.accounts;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.accounts.AccountDtos.AccountResponse;
import com.abdullah.msal2.accounts.AccountDtos.UpdateAccountRequest;
import com.abdullah.msal2.budget.Budget;
import com.abdullah.msal2.budget.BudgetRepository;
import com.abdullah.msal2.expenses.EntryRules;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.persistence.Versions;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-004: accounts are created, renamed, archived and restored, never deleted (R-ACC).
 */
@Service
public class AccountService {

	/** The default account of a new budget (R-ACC). */
	public static final String DEFAULT_NAME = "مصاريفي";

	private final AccountRepository accounts;
	private final BudgetRepository budgets;
	private final AuditService audit;

	public AccountService(AccountRepository accounts, BudgetRepository budgets, AuditService audit) {
		this.accounts = accounts;
		this.budgets = budgets;
		this.audit = audit;
	}

	/** The user's account, else {@code SYS-E004}. */
	@Transactional(readOnly = true)
	public Account owned(UUID userId, UUID accountId) {
		return accounts.findOwned(accountId, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> list(UUID userId, UUID budgetId, boolean includeArchived) {
		ownBudget(userId, budgetId);
		return accounts.findByBudgetIdOrderBySortOrderAscCreatedAtAsc(budgetId).stream()
				.filter(a -> includeArchived || !a.isArchived())
				.map(AccountResponse::of)
				.toList();
	}

	/** A new account becomes the current one (R-ACC). */
	@Transactional
	public AccountResponse create(UUID userId, UUID budgetId, String rawName) {
		Budget budget = ownBudget(userId, budgetId);
		String name = requireName(rawName);
		Account account = accounts.saveAndFlush(new Account(userId, budgetId, name, accounts.maxSortOrder(budgetId) + 1));
		budget.setLastAccountId(account.getId());
		audit.record(userId, AuditAction.CREATE, "account", account.getId(), null, Map.of("name", name));
		return AccountResponse.of(account);
	}

	/** Creates the default account of a new budget. */
	@Transactional
	public Account createDefault(UUID userId, UUID budgetId) {
		return accounts.saveAndFlush(new Account(userId, budgetId, DEFAULT_NAME, 1));
	}

	@Transactional
	public AccountResponse update(UUID userId, UUID accountId, Patch<UpdateAccountRequest> patch) {
		Account account = owned(userId, accountId);
		UpdateAccountRequest request = patch.value();
		Versions.check(account, request.version());
		if (patch.has("name")) {
			account.setName(requireName(request.name()));
		}
		if (request.isArchived() != null && request.isArchived() != account.isArchived()) {
			if (request.isArchived()) {
				List<AccountRules.AccountData> all = accounts.findByBudgetIdOrderBySortOrderAscCreatedAtAsc(account.getBudgetId())
						.stream().map(Account::toData).toList();
				if (!AccountRules.canArchive(all, accountId)) {
					throw new ApiException(ErrorCode.SRV_004_E002);
				}
			}
			account.setArchived(request.isArchived());
			audit.record(userId, AuditAction.UPDATE, "account", accountId, Map.of("isArchived", !request.isArchived()),
					Map.of("isArchived", request.isArchived()));
		}
		return AccountResponse.of(accounts.saveAndFlush(account));
	}

	private Budget ownBudget(UUID userId, UUID budgetId) {
		return budgets.findByIdAndUserIdAndDeletedAtIsNull(budgetId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}

	private static String requireName(String name) {
		String clean = EntryRules.cleanName(name);
		if (clean.isEmpty()) {
			throw new ApiException(ErrorCode.SRV_004_E001);
		}
		return clean;
	}
}
