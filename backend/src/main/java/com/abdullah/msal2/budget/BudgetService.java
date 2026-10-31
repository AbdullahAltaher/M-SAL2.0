package com.abdullah.msal2.budget;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.accounts.Account;
import com.abdullah.msal2.accounts.AccountRepository;
import com.abdullah.msal2.accounts.AccountService;
import com.abdullah.msal2.budget.BudgetDtos.BudgetListItem;
import com.abdullah.msal2.budget.BudgetDtos.BudgetResponse;
import com.abdullah.msal2.budget.BudgetDtos.GroupResponse;
import com.abdullah.msal2.budget.BudgetDtos.GroupTotals;
import com.abdullah.msal2.budget.BudgetDtos.ItemResponse;
import com.abdullah.msal2.budget.BudgetDtos.Salary;
import com.abdullah.msal2.budget.BudgetDtos.Totals;
import com.abdullah.msal2.budget.BudgetDtos.UpdateBudgetRequest;
import com.abdullah.msal2.budget.BudgetDtos.UpdateGroupRequest;
import com.abdullah.msal2.budget.BudgetDtos.UpdateItemRequest;
import com.abdullah.msal2.categories.CategoryService;
import com.abdullah.msal2.expenses.EntryRules;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.persistence.Versions;
import com.abdullah.msal2.system.web.Inputs;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-002: budgets ("people" in M-SAL) and their monthly ledger (R-BUD-01 to R-BUD-06).
 */
@Service
public class BudgetService {

	/** The name of the budget created on the first sign-in (R-BUD-06). */
	public static final String FIRST_BUDGET_NAME = "الأساسي";

	/** The group a new budget starts with (R-BUD-06). */
	public static final String FIRST_GROUP_TITLE = "التزامات ثابتة";

	private final BudgetRepository budgets;
	private final BudgetGroupRepository groups;
	private final BudgetItemRepository items;
	private final AccountRepository accounts;
	private final AccountService accountService;
	private final CategoryService categoryService;
	private final AuditService audit;
	private final JdbcTemplate jdbc;
	private final Clock clock;

	public BudgetService(BudgetRepository budgets, BudgetGroupRepository groups, BudgetItemRepository items,
			AccountRepository accounts, AccountService accountService, CategoryService categoryService,
			AuditService audit, JdbcTemplate jdbc, Clock clock) {
		this.budgets = budgets;
		this.groups = groups;
		this.items = items;
		this.accounts = accounts;
		this.accountService = accountService;
		this.categoryService = categoryService;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ── Budgets ─────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<BudgetListItem> list(UUID userId) {
		return budgets.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(userId).stream()
				.map(b -> new BudgetListItem(b.getId(), b.getName(), b.getMonthLabel(), b.getSortOrder(), b.getVersion()))
				.toList();
	}

	@Transactional(readOnly = true)
	public BudgetResponse get(UUID userId, UUID budgetId) {
		return toResponse(owned(userId, budgetId));
	}

	/** A new budget with a name (R-BUD-06). It becomes the open one. */
	@Transactional
	public BudgetResponse create(UUID userId, String rawName, LocalDate today) {
		String name = EntryRules.cleanName(rawName);
		if (name.isEmpty()) {
			throw new ApiException(ErrorCode.SRV_002_E001);
		}
		Budget budget = createWithDefaults(userId, name, today);
		jdbc.update("update msal.app_users set last_budget_id = ? where id = ?", budget.getId(), userId);
		return toResponse(budget);
	}

	/**
	 * A budget with everything a new one starts with (R-BUD-06): this month's name, "الراتب",
	 * one group "التزامات ثابتة" with an empty item, the account "مصاريفي" and the 20 categories.
	 */
	@Transactional
	public Budget createWithDefaults(UUID userId, String name, LocalDate today) {
		Budget budget = budgets.saveAndFlush(
				new Budget(userId, name, MonthLabel.of(today), budgets.maxSortOrder(userId) + 1));
		BudgetGroup group = groups.save(new BudgetGroup(userId, budget.getId(), FIRST_GROUP_TITLE, 1));
		items.save(new BudgetItem(userId, group.getId(), 1));
		Account account = accountService.createDefault(userId, budget.getId());
		categoryService.copyTemplates(userId, budget.getId());
		budget.setLastAccountId(account.getId());
		budgets.saveAndFlush(budget);
		audit.record(userId, AuditAction.CREATE, "budget", budget.getId(), null, Map.of("name", name));
		return budget;
	}

	@Transactional
	public BudgetResponse update(UUID userId, UUID budgetId, Patch<UpdateBudgetRequest> patch) {
		Budget budget = owned(userId, budgetId);
		UpdateBudgetRequest request = patch.value();
		Versions.check(budget, request.version());
		if (patch.has("name")) {
			String name = EntryRules.cleanName(request.name());
			if (name.isEmpty()) {
				throw new ApiException(ErrorCode.SRV_002_E001);
			}
			budget.setName(name);
		}
		if (patch.has("monthLabel")) {
			budget.setMonthLabel(request.monthLabel() == null ? "" : request.monthLabel());
		}
		if (patch.has("salaryLabel")) {
			budget.setSalaryLabel(request.salaryLabel() == null ? "" : request.salaryLabel());
		}
		if (patch.has("salary")) {
			if (request.salary() == null) {
				budget.setSalary(null, false);
			}
			else {
				budget.setSalary(Inputs.budgetAmount(request.salary().amount()), request.salary().isApprox());
			}
		}
		if (patch.has("lastAccountId") && request.lastAccountId() != null) {
			Account account = accounts.findOwned(request.lastAccountId(), userId)
					.filter(a -> a.getBudgetId().equals(budgetId))
					.orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
			budget.setLastAccountId(account.getId());
		}
		return toResponse(budgets.saveAndFlush(budget));
	}

	/** A soft delete. The last budget cannot be deleted (R-BUD-06). */
	@Transactional
	public void delete(UUID userId, UUID budgetId) {
		Budget budget = owned(userId, budgetId);
		if (budgets.countByUserIdAndDeletedAtIsNull(userId) <= 1) {
			throw new ApiException(ErrorCode.SRV_002_E002);
		}
		budget.softDelete(Instant.now(clock));
		budgets.saveAndFlush(budget);
		UUID next = budgets.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(userId).getFirst().getId();
		jdbc.update("update msal.app_users set last_budget_id = ? where id = ? and last_budget_id = ?", next, userId,
				budgetId);
		audit.record(userId, AuditAction.DELETE, "budget", budgetId, Map.of("name", budget.getName()), null);
	}

	/** Clears every "done" mark and sets this month's name; items and amounts stay (R-BUD-05). */
	@Transactional
	public BudgetResponse newMonth(UUID userId, UUID budgetId, LocalDate today) {
		Budget budget = owned(userId, budgetId);
		List<UUID> groupIds = groups.findByBudgetIdOrderBySortOrderAscCreatedAtAsc(budgetId).stream()
				.map(BudgetGroup::getId).toList();
		List<UUID> wasDone = new ArrayList<>();
		for (BudgetItem item : itemsOf(groupIds)) {
			if (item.isDone()) {
				wasDone.add(item.getId());
				item.setDone(false);
			}
		}
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("monthLabel", budget.getMonthLabel());
		before.put("doneItemIds", wasDone);
		budget.setMonthLabel(MonthLabel.of(today));
		budgets.saveAndFlush(budget);
		audit.record(userId, AuditAction.NEW_MONTH, "budget", budgetId, before,
				Map.of("monthLabel", budget.getMonthLabel()));
		return toResponse(budget);
	}

	// ── Groups ──────────────────────────────────────────────────

	/** A new group comes with one empty item (R-BUD-04). */
	@Transactional
	public GroupResponse addGroup(UUID userId, UUID budgetId, String title) {
		Budget budget = owned(userId, budgetId);
		BudgetGroup group = groups.saveAndFlush(new BudgetGroup(userId, budgetId, title == null ? "" : title,
				groups.maxSortOrder(budgetId) + 1));
		items.saveAndFlush(new BudgetItem(userId, group.getId(), 1));
		return toResponse(budget).groups().stream().filter(g -> g.id().equals(group.getId())).findFirst().orElseThrow();
	}

	@Transactional
	public GroupResponse updateGroup(UUID userId, UUID groupId, Patch<UpdateGroupRequest> patch) {
		BudgetGroup group = ownedGroup(userId, groupId);
		Versions.check(group, patch.value().version());
		if (patch.has("title")) {
			group.setTitle(patch.value().title() == null ? "" : patch.value().title());
		}
		groups.saveAndFlush(group);
		Budget budget = owned(userId, group.getBudgetId());
		return toResponse(budget).groups().stream().filter(g -> g.id().equals(groupId)).findFirst().orElseThrow();
	}

	/** A hard delete of the group and its items, kept in {@code audit_log} (DATABASE.md D-07). */
	@Transactional
	public void deleteGroup(UUID userId, UUID groupId) {
		BudgetGroup group = ownedGroup(userId, groupId);
		List<ItemResponse> groupItems = items.findByGroupIdOrderBySortOrderAscCreatedAtAsc(groupId).stream()
				.map(ItemResponse::of).toList();
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("budgetId", group.getBudgetId());
		before.put("title", group.getTitle());
		before.put("sortOrder", group.getSortOrder());
		before.put("items", groupItems);
		items.deleteAllInBatch(items.findByGroupIdOrderBySortOrderAscCreatedAtAsc(groupId));
		groups.delete(group);
		groups.flush();
		audit.record(userId, AuditAction.DELETE, "budget_group", groupId, before, null);
	}

	// ── Items ───────────────────────────────────────────────────

	/** A new empty item at the end of the group (R-BUD-04). */
	@Transactional
	public ItemResponse addItem(UUID userId, UUID groupId) {
		BudgetGroup group = ownedGroup(userId, groupId);
		BudgetItem item = items.saveAndFlush(new BudgetItem(userId, group.getId(), items.maxSortOrder(groupId) + 1));
		return ItemResponse.of(item);
	}

	@Transactional
	public ItemResponse updateItem(UUID userId, UUID itemId, Patch<UpdateItemRequest> patch) {
		BudgetItem item = items.findOwned(itemId, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
		UpdateItemRequest request = patch.value();
		Versions.check(item, request.version());
		if (patch.has("name")) {
			item.setName(request.name() == null ? "" : request.name());
		}
		if (patch.has("amount")) {
			item.setAmount(Inputs.budgetAmount(request.amount()));
		}
		if (request.isApprox() != null) {
			item.setApprox(request.isApprox());
		}
		if (request.isDone() != null) {
			item.setDone(request.isDone());
		}
		return ItemResponse.of(items.saveAndFlush(item));
	}

	/** A hard delete, kept in {@code audit_log}. Items are deleted without asking (R-BUD-04). */
	@Transactional
	public void deleteItem(UUID userId, UUID itemId) {
		BudgetItem item = items.findOwned(itemId, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("groupId", item.getGroupId());
		before.put("item", ItemResponse.of(item));
		items.delete(item);
		items.flush();
		audit.record(userId, AuditAction.DELETE, "budget_item", itemId, before, null);
	}

	// ── Helpers ─────────────────────────────────────────────────

	/** The user's live budget, else {@code SYS-E004}. */
	@Transactional(readOnly = true)
	public Budget owned(UUID userId, UUID budgetId) {
		return budgets.findByIdAndUserIdAndDeletedAtIsNull(budgetId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}

	private List<BudgetItem> itemsOf(Collection<UUID> groupIds) {
		return groupIds.isEmpty() ? List.of() : items.findByGroupIdInOrderBySortOrderAscCreatedAtAsc(groupIds);
	}

	private BudgetGroup ownedGroup(UUID userId, UUID groupId) {
		return groups.findOwned(groupId, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}

	/** The budget with its groups, items and totals (R-BUD-01, R-BUD-02). */
	private BudgetResponse toResponse(Budget budget) {
		List<BudgetGroup> budgetGroups = groups.findByBudgetIdOrderBySortOrderAscCreatedAtAsc(budget.getId());
		Map<UUID, List<BudgetItem>> byGroup = new LinkedHashMap<>();
		budgetGroups.forEach(g -> byGroup.put(g.getId(), new ArrayList<>()));
		itemsOf(byGroup.keySet()).forEach(i -> byGroup.get(i.getGroupId()).add(i));

		BudgetTotals.Result totals = BudgetTotals.compute(
				new BudgetTotals.Amount(budget.getSalaryAmount(), budget.isSalaryIsApprox()),
				budgetGroups.stream()
						.map(g -> byGroup.get(g.getId()).stream()
								.map(i -> new BudgetTotals.Amount(i.getAmount(), i.isApprox())).toList())
						.toList());

		List<GroupResponse> groupResponses = new ArrayList<>();
		for (int i = 0; i < budgetGroups.size(); i++) {
			BudgetGroup g = budgetGroups.get(i);
			BudgetTotals.GroupTotal t = totals.groups().get(i);
			groupResponses.add(new GroupResponse(g.getId(), g.getTitle(), g.getSortOrder(),
					byGroup.get(g.getId()).stream().map(ItemResponse::of).toList(),
					new GroupTotals(t.subtotal(), t.subtotalApprox(), t.balanceAfter(), t.balanceApprox()),
					g.getVersion()));
		}
		return new BudgetResponse(budget.getId(), budget.getName(), budget.getMonthLabel(), budget.getSalaryLabel(),
				new Salary(budget.getSalaryAmount(), budget.isSalaryIsApprox()), groupResponses,
				new Totals(totals.finalBalance(), totals.finalApprox()), budget.getLastAccountId(), budget.getVersion());
	}
}
