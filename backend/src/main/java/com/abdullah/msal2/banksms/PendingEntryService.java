package com.abdullah.msal2.banksms;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.banksms.BankSmsDtos.ApproveRequest;
import com.abdullah.msal2.banksms.BankSmsDtos.PendingEntryResponse;
import com.abdullah.msal2.banksms.CategorySuggester.Suggestion;
import com.abdullah.msal2.budget.BudgetService;
import com.abdullah.msal2.categories.Category;
import com.abdullah.msal2.categories.CategoryService;
import com.abdullah.msal2.expenses.EntryDtos.CreateEntryRequest;
import com.abdullah.msal2.expenses.EntryDtos.EntryResponse;
import com.abdullah.msal2.expenses.EntryService;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * The review of pending entries (B-18): each one alone is approved, and becomes a normal entry,
 * or ignored (B-05, B-09). Either way it is deleted for good, with its message (B-07).
 */
@Service
public class PendingEntryService {

	private final PendingEntries pending;
	private final MerchantKeywords keywords;
	private final CategorySuggester suggester;
	private final BudgetService budgets;
	private final CategoryService categories;
	private final EntryService entries;
	private final AuditService audit;
	private final Clock clock;

	public PendingEntryService(PendingEntries pending, MerchantKeywords keywords, CategorySuggester suggester,
			BudgetService budgets, CategoryService categories, EntryService entries, AuditService audit, Clock clock) {
		this.pending = pending;
		this.keywords = keywords;
		this.suggester = suggester;
		this.budgets = budgets;
		this.categories = categories;
		this.entries = entries;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Newest first. The suggestion is made again from the words as they are now, so a merchant
	 * remembered on one card is suggested on the others.
	 */
	@Transactional(readOnly = true)
	public List<PendingEntryResponse> list(UUID userId, UUID budgetId) {
		budgets.owned(userId, budgetId);
		Function<String, Suggestion> suggest = suggester.forBudget(budgetId);
		List<UUID> visible = categories.inBudget(budgetId).stream().filter(c -> !c.isHidden()).map(Category::getId)
				.toList();
		return pending.ofBudget(budgetId).stream().map(p -> {
			Suggestion s = suggest.apply(p.merchant());
			UUID categoryId = s.category().map(Category::getId).orElse(null);
			if (categoryId == null && !s.askEachTime() && visible.contains(p.suggestedCategoryId())) {
				categoryId = p.suggestedCategoryId();
			}
			return new PendingEntryResponse(p.id(), p.accountId(), p.amount(), p.merchant(), categoryId,
					s.askEachTime(), p.receivedAt(), p.entryDate(), p.smsText());
		}).toList();
	}

	/**
	 * Adds the entry exactly as {@code POST /entries} does (the same checks, cycle and messages),
	 * then deletes the pending entry. The category is required (SRV-011-E004).
	 */
	@Transactional
	public EntryResponse approve(UUID userId, UUID id, ApproveRequest request) {
		PendingEntries.Row p = owned(userId, id);
		if (request.categoryId() == null) {
			throw new ApiException(ErrorCode.SRV_011_E004);
		}
		// Another user's category is not found, never "forbidden" (rule 6)
		Category category = categories.find(userId, request.categoryId())
				.filter(c -> c.getBudgetId().equals(p.budgetId()))
				.orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
		EntryResponse entry = entries.create(userId, p.budgetId(), new CreateEntryRequest("EXPENSE",
				p.amount().toPlainString(), request.name(), category.getId(), p.entryDate().toString(), p.accountId()));
		if (Boolean.TRUE.equals(request.remember())) {
			// The whole cleaned name, not its first word: "AL SARAB CAFETERIA" starts with "AL" (4.4)
			String word = MerchantClassifier.clean(p.merchant());
			if (!word.isEmpty() && word.length() <= SmsSettingsService.MAX_KEYWORD) {
				keywords.save(userId, p.budgetId(), word, category.getId(), Instant.now(clock));
			}
		}
		pending.delete(p.id());
		audit.record(userId, AuditAction.PURGE, "pending_entry", p.id(), p.withoutText(), Map.of("entryId", entry.id()));
		return entry;
	}

	/** "تجاهل": an operation the user does not want to record (B-05). */
	@Transactional
	public void ignore(UUID userId, UUID id) {
		PendingEntries.Row p = owned(userId, id);
		pending.delete(p.id());
		audit.record(userId, AuditAction.PURGE, "pending_entry", p.id(), p.withoutText(), null);
	}

	private PendingEntries.Row owned(UUID userId, UUID id) {
		return pending.owned(id, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}
}
