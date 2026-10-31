package com.abdullah.msal2.expenses;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.accounts.Account;
import com.abdullah.msal2.accounts.AccountRepository;
import com.abdullah.msal2.budget.Budget;
import com.abdullah.msal2.budget.BudgetService;
import com.abdullah.msal2.categories.Category;
import com.abdullah.msal2.categories.CategoryService;
import com.abdullah.msal2.cycles.CycleData;
import com.abdullah.msal2.cycles.CycleKey;
import com.abdullah.msal2.cycles.CycleSummary;
import com.abdullah.msal2.cycles.Cycles;
import com.abdullah.msal2.cycles.Ledger;
import com.abdullah.msal2.cycles.LedgerService;
import com.abdullah.msal2.cycles.SalaryCycle;
import com.abdullah.msal2.cycles.SalaryCycleRepository;
import com.abdullah.msal2.expenses.EntryDtos.AccountRef;
import com.abdullah.msal2.expenses.EntryDtos.CreateEntryRequest;
import com.abdullah.msal2.expenses.EntryDtos.CycleRef;
import com.abdullah.msal2.expenses.EntryDtos.DayResponse;
import com.abdullah.msal2.expenses.EntryDtos.DaysResponse;
import com.abdullah.msal2.expenses.EntryDtos.EntryResponse;
import com.abdullah.msal2.expenses.EntryDtos.HomeResponse;
import com.abdullah.msal2.expenses.EntryDtos.Selected;
import com.abdullah.msal2.expenses.EntryDtos.UpdateEntryRequest;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.money.Money;
import com.abdullah.msal2.system.persistence.Versions;
import com.abdullah.msal2.system.web.Inputs;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-001: expenses and extra income (R-ENT-01 to R-ENT-12), and the home screen
 * (R-CYC-04 to R-CYC-06).
 */
@Service
public class EntryService {

	/** How many entries the home screen lists (R-CYC-06). */
	static final int RECENT = 5;

	private final EntryRepository entries;
	private final LedgerService ledgers;
	private final AccountRepository accounts;
	private final SalaryCycleRepository cycles;
	private final BudgetService budgets;
	private final CategoryService categories;
	private final AuditService audit;
	private final Clock clock;

	public EntryService(EntryRepository entries, LedgerService ledgers, AccountRepository accounts,
			SalaryCycleRepository cycles, BudgetService budgets, CategoryService categories, AuditService audit,
			Clock clock) {
		this.entries = entries;
		this.ledgers = ledgers;
		this.accounts = accounts;
		this.cycles = cycles;
		this.budgets = budgets;
		this.categories = categories;
		this.audit = audit;
		this.clock = clock;
	}

	// ── Reading ─────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public HomeResponse home(UUID userId, UUID accountId, String cycleKey, LocalDate today) {
		Ledger ledger = ledgers.load(userId, accountId);
		Optional<CycleData> selected = CycleKey.resolve(cycleKey, ledger.cycles());
		List<EntryData> list = CycleKey.entriesOf(selected, ledger.cycles(), ledger.entries());

		Selected selection = selected
				.map(c -> {
					Cycles.Bounds b = Cycles.bounds(ledger.cycles(), c);
					return new Selected(c.id().toString(),
							new CycleRef(c.id(), c.startDate(), c.expectedNextSalaryDate(), b.lastDay(), b.open()),
							CycleSummary.of(ledger.cycles(), c, ledger.entries(), today), null);
				})
				.orElseGet(() -> new Selected(CycleKey.NONE, null, null,
						Money.sum(list.stream().filter(EntryData::isExpense).map(EntryData::amount).toList())));

		List<EntryResponse> recent = list.stream().sorted(EntryDays.NEWEST_FIRST).limit(RECENT)
				.map(e -> EntryResponse.of(ledger.entities().get(e.id())))
				.toList();
		List<AccountRef> active = accounts.findByBudgetIdOrderBySortOrderAscCreatedAtAsc(ledger.account().getBudgetId())
				.stream().filter(a -> !a.isArchived()).map(a -> new AccountRef(a.getId(), a.getName())).toList();
		return new HomeResponse(today, new AccountRef(accountId, ledger.account().getName()), active,
				CycleKey.all(ledger.cycles(), ledger.entries()), selection, recent);
	}

	/** The entries of a cycle grouped by day, filtered by text and category (R-ENT-10, R-ENT-11). */
	@Transactional(readOnly = true)
	public DaysResponse list(UUID userId, UUID accountId, String cycleKey, String query, UUID categoryId) {
		Ledger ledger = ledgers.load(userId, accountId);
		List<EntryData> list = CycleKey.entriesOf(CycleKey.resolve(cycleKey, ledger.cycles()), ledger.cycles(),
				ledger.entries());
		Map<UUID, String> names = categories.names(ledger.account().getBudgetId());
		List<EntryData> filtered = EntrySearch.filter(list, query, categoryId, categoryName(names));
		return new DaysResponse(EntryDays.group(filtered).stream()
				.map(d -> new DayResponse(d.date(), d.spent(), d.extra(),
						d.entries().stream().map(e -> EntryResponse.of(ledger.entities().get(e.id()))).toList()))
				.toList());
	}

	/** The expense for "repeat last" (R-ENT-08), or empty. */
	@Transactional(readOnly = true)
	public Optional<EntryResponse> lastExpense(UUID userId, UUID accountId) {
		Ledger ledger = ledgers.load(userId, accountId);
		return EntryRules.lastExpense(ledger.entries()).map(e -> EntryResponse.of(ledger.entities().get(e.id())));
	}

	@Transactional(readOnly = true)
	public EntryResponse get(UUID userId, UUID entryId) {
		return EntryResponse.of(owned(userId, entryId));
	}

	/** The trash, most recently deleted first (R-ENT-09). */
	@Transactional(readOnly = true)
	public List<EntryResponse> deleted(UUID userId, UUID budgetId) {
		budgets.owned(userId, budgetId);
		return entries.findByBudgetIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(budgetId).stream()
				.map(EntryResponse::of).toList();
	}

	/** Up to 4 descriptions for a category (R-ENT-12). */
	@Transactional(readOnly = true)
	public List<String> suggestions(UUID userId, UUID budgetId, UUID categoryId) {
		budgets.owned(userId, budgetId);
		Category category = categories.owned(userId, categoryId);
		if (!category.getBudgetId().equals(budgetId)) {
			throw new ApiException(ErrorCode.SYS_E004);
		}
		List<EntryData> history = entries.findByBudgetIdAndCategoryIdAndDeletedAtIsNull(budgetId, categoryId).stream()
				.map(Entry::toData).toList();
		return NameSuggestions.suggest(history, categoryId, NameSuggestions.DEFAULT_LIMIT,
				categories.starterSuggestions(category));
	}

	// ── Changing ────────────────────────────────────────────────

	/** Checked in the order of R-ENT-02: amount, category, description, date, account. */
	@Transactional
	public EntryResponse create(UUID userId, UUID budgetId, CreateEntryRequest request) {
		Budget budget = budgets.owned(userId, budgetId);
		EntryType type = type(request.type());
		BigDecimal amount = Inputs.entryAmount(request.amount());
		UUID categoryId = category(userId, budgetId, request.categoryId());
		String name = name(request.name());
		LocalDate date = Inputs.date(request.entryDate(), ErrorCode.SRV_001_E004);
		UUID accountId = account(userId, budgetId, request.accountId());

		Entry entry = entries.saveAndFlush(new Entry(userId, budget.getId(), accountId, categoryId, type, amount, name, date));
		return EntryResponse.of(entry, cycleKeyOf(entry));
	}

	@Transactional
	public EntryResponse update(UUID userId, UUID entryId, Patch<UpdateEntryRequest> patch) {
		Entry entry = owned(userId, entryId);
		if (entry.isDeleted()) {
			throw new ApiException(ErrorCode.SYS_E004);
		}
		UpdateEntryRequest request = patch.value();
		Versions.check(entry, request.version());
		UUID budgetId = entry.getBudgetId();
		if (patch.has("type")) {
			entry.setType(type(request.type()));
		}
		if (patch.has("amount")) {
			entry.setAmount(Inputs.entryAmount(request.amount()));
		}
		if (patch.has("categoryId")) {
			entry.setCategoryId(category(userId, budgetId, request.categoryId()));
		}
		if (patch.has("name")) {
			entry.setName(name(request.name()));
		}
		if (patch.has("entryDate")) {
			entry.setEntryDate(Inputs.date(request.entryDate(), ErrorCode.SRV_001_E004));
		}
		if (patch.has("accountId")) {
			entry.setAccountId(account(userId, budgetId, request.accountId()));
		}
		Entry saved = entries.saveAndFlush(entry);
		return EntryResponse.of(saved, cycleKeyOf(saved));
	}

	/** A soft delete, undone with restore (R-ENT-09). */
	@Transactional
	public void delete(UUID userId, UUID entryId) {
		Entry entry = owned(userId, entryId);
		if (!entry.isDeleted()) {
			entry.softDelete(Instant.now(clock));
			entries.saveAndFlush(entry);
			audit.record(userId, AuditAction.DELETE, "entry", entryId, null, null);
		}
	}

	@Transactional
	public EntryResponse restore(UUID userId, UUID entryId) {
		Entry entry = owned(userId, entryId);
		if (!entry.isDeleted()) {
			throw new ApiException(ErrorCode.SRV_001_E006);
		}
		entry.restore();
		Entry saved = entries.saveAndFlush(entry);
		audit.record(userId, AuditAction.RESTORE, "entry", entryId, null, null);
		return EntryResponse.of(saved, cycleKeyOf(saved));
	}

	/** A copy dated today, with a new id and creation time (R-ENT-07). */
	@Transactional
	public EntryResponse duplicate(UUID userId, UUID entryId, LocalDate today) {
		Entry entry = owned(userId, entryId);
		if (entry.isDeleted()) {
			throw new ApiException(ErrorCode.SYS_E004);
		}
		Entry copy = entries.saveAndFlush(entry.copyFor(today));
		return EntryResponse.of(copy, cycleKeyOf(copy));
	}

	/** A hard delete, kept in {@code audit_log} (R-ENT-07 undo, R-ENT-09 trash). */
	@Transactional
	public void purge(UUID userId, UUID entryId) {
		Entry entry = owned(userId, entryId);
		EntryResponse before = EntryResponse.of(entry);
		entries.delete(entry);
		entries.flush();
		audit.record(userId, AuditAction.PURGE, "entry", entryId, before, null);
	}

	/** Empties the trash of a budget (R-ENT-09). */
	@Transactional
	public int purgeDeleted(UUID userId, UUID budgetId) {
		budgets.owned(userId, budgetId);
		List<Entry> trash = entries.findByBudgetIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(budgetId);
		if (trash.isEmpty()) {
			return 0;
		}
		List<EntryResponse> before = trash.stream().map(EntryResponse::of).toList();
		entries.deleteAllInBatch(trash);
		audit.record(userId, AuditAction.PURGE, "entry", null, Map.of("entries", before), null);
		return trash.size();
	}

	// ── Helpers ─────────────────────────────────────────────────

	private Entry owned(UUID userId, UUID entryId) {
		return entries.findOwned(entryId, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}

	private String cycleKeyOf(Entry entry) {
		List<CycleData> accountCycles = cycles.findByAccountId(entry.getAccountId()).stream()
				.map(SalaryCycle::toData).toList();
		return CycleKey.of(accountCycles, entry.toData());
	}

	/** {@code EXPENSE} unless {@code INCOME}; anything else is invalid. */
	private static EntryType type(String value) {
		if (value == null || value.equals("EXPENSE")) {
			return EntryType.EXPENSE;
		}
		if (value.equals("INCOME")) {
			return EntryType.INCOME;
		}
		throw new ApiException(ErrorCode.SYS_E006);
	}

	/** A category of this budget, else {@code SRV-001-E002}. */
	private UUID category(UUID userId, UUID budgetId, UUID categoryId) {
		if (categoryId == null) {
			throw new ApiException(ErrorCode.SRV_001_E002);
		}
		// Another user's or unknown category gets the same answer as a missing one
		return categories.find(userId, categoryId)
				.filter(c -> c.getBudgetId().equals(budgetId))
				.map(Category::getId)
				.orElseThrow(() -> new ApiException(ErrorCode.SRV_001_E002));
	}

	/** A description, trimmed; spaces alone do not count (R-ENT-02). */
	private static String name(String value) {
		if (!EntryRules.hasName(value)) {
			throw new ApiException(ErrorCode.SRV_001_E003);
		}
		return EntryRules.cleanName(value);
	}

	/** An account of this budget, else {@code SRV-001-E005}. Archived accounts are allowed. */
	private UUID account(UUID userId, UUID budgetId, UUID accountId) {
		if (accountId == null) {
			throw new ApiException(ErrorCode.SRV_001_E005);
		}
		return accounts.findOwned(accountId, userId)
				.filter(a -> a.getBudgetId().equals(budgetId))
				.map(Account::getId)
				.orElseThrow(() -> new ApiException(ErrorCode.SRV_001_E005));
	}

	static Function<UUID, String> categoryName(Map<UUID, String> names) {
		return id -> names.getOrDefault(id, CategoryService.FALLBACK_NAME);
	}
}
