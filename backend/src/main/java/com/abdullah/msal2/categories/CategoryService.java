package com.abdullah.msal2.categories;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.budget.BudgetRepository;
import com.abdullah.msal2.categories.CategoryDtos.CategoryResponse;
import com.abdullah.msal2.categories.CategoryDtos.CreateCategoryRequest;
import com.abdullah.msal2.categories.CategoryDtos.UpdateCategoryRequest;
import com.abdullah.msal2.expenses.EntryRules;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.persistence.Versions;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-005: categories are renamed, hidden and reordered, never deleted (R-CAT).
 */
@Service
public class CategoryService {

	/** What a missing category shows as (R-CAT). */
	public static final String FALLBACK_NAME = "أخرى";

	private final CategoryRepository categories;
	private final CategoryTemplateRepository templates;
	private final BudgetRepository budgets;
	private final AuditService audit;

	public CategoryService(CategoryRepository categories, CategoryTemplateRepository templates, BudgetRepository budgets,
			AuditService audit) {
		this.categories = categories;
		this.templates = templates;
		this.budgets = budgets;
		this.audit = audit;
	}

	/** Copies the 20 default categories into a new budget (R-BUD-06). */
	@Transactional
	public void copyTemplates(UUID userId, UUID budgetId) {
		for (CategoryTemplate t : templates.findAllByOrderBySortOrderAsc()) {
			categories.save(new Category(userId, budgetId, t.getCode(), t.getNameAr(), t.getEmoji(), t.getSortOrder()));
		}
	}

	/** Category id to display name, for a budget. */
	@Transactional(readOnly = true)
	public Map<UUID, String> names(UUID budgetId) {
		Map<UUID, String> names = new LinkedHashMap<>();
		categories.findByBudgetIdOrderBySortOrderAscCreatedAtAsc(budgetId).forEach(c -> names.put(c.getId(), c.getName()));
		return names;
	}

	/** A budget's categories in the person's order; the caller checks the budget is theirs. */
	@Transactional(readOnly = true)
	public List<Category> inBudget(UUID budgetId) {
		return categories.findByBudgetIdOrderBySortOrderAscCreatedAtAsc(budgetId);
	}

	/** The starter suggestions of a default category, or none (R-ENT-12). */
	@Transactional(readOnly = true)
	public List<String> starterSuggestions(Category category) {
		if (category.getCode() == null) {
			return List.of();
		}
		return templates.findById(category.getCode()).map(CategoryTemplate::getDefaultSuggestions).orElse(List.of());
	}

	/** The user's category, if any. Never throws, so callers can answer with their own code. */
	@Transactional(readOnly = true)
	public Optional<Category> find(UUID userId, UUID categoryId) {
		return categories.findOwned(categoryId, userId);
	}

	/** The user's category, else {@code SYS-E004}. */
	@Transactional(readOnly = true)
	public Category owned(UUID userId, UUID categoryId) {
		return categories.findOwned(categoryId, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> list(UUID userId, UUID budgetId) {
		ownBudget(userId, budgetId);
		return categories.findByBudgetIdOrderBySortOrderAscCreatedAtAsc(budgetId).stream().map(CategoryResponse::of).toList();
	}

	@Transactional
	public CategoryResponse create(UUID userId, UUID budgetId, CreateCategoryRequest request) {
		ownBudget(userId, budgetId);
		String name = requireName(request.name());
		String emoji = requireEmoji(request.emoji());
		Category category = categories.saveAndFlush(
				new Category(userId, budgetId, null, name, emoji, categories.maxSortOrder(budgetId) + 1));
		audit.record(userId, AuditAction.CREATE, "category", category.getId(), null, Map.of("name", name));
		return CategoryResponse.of(category);
	}

	@Transactional
	public CategoryResponse update(UUID userId, UUID categoryId, Patch<UpdateCategoryRequest> patch) {
		Category category = owned(userId, categoryId);
		UpdateCategoryRequest request = patch.value();
		Versions.check(category, request.version());
		if (patch.has("name")) {
			category.setName(requireName(request.name()));
		}
		if (patch.has("emoji")) {
			category.setEmoji(requireEmoji(request.emoji()));
		}
		if (request.isHidden() != null) {
			category.setHidden(request.isHidden());
		}
		return CategoryResponse.of(categories.saveAndFlush(category));
	}

	/** The new order must list exactly the budget's categories (R-CAT). */
	@Transactional
	public List<CategoryResponse> reorder(UUID userId, UUID budgetId, List<UUID> categoryIds) {
		ownBudget(userId, budgetId);
		List<Category> current = categories.findByBudgetIdOrderBySortOrderAscCreatedAtAsc(budgetId);
		Map<UUID, Category> byId = new LinkedHashMap<>();
		current.forEach(c -> byId.put(c.getId(), c));
		if (categoryIds.size() != current.size() || !new HashSet<>(categoryIds).equals(byId.keySet())) {
			throw new ApiException(ErrorCode.SRV_005_E003);
		}
		for (int i = 0; i < categoryIds.size(); i++) {
			byId.get(categoryIds.get(i)).setSortOrder(i + 1);
		}
		categories.flush();
		return categoryIds.stream().map(id -> CategoryResponse.of(byId.get(id))).toList();
	}

	private void ownBudget(UUID userId, UUID budgetId) {
		budgets.findByIdAndUserIdAndDeletedAtIsNull(budgetId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}

	private static String requireName(String name) {
		String clean = EntryRules.cleanName(name);
		if (clean.isEmpty()) {
			throw new ApiException(ErrorCode.SRV_005_E001);
		}
		return clean;
	}

	private static String requireEmoji(String emoji) {
		String clean = EntryRules.cleanName(emoji);
		if (clean.isEmpty()) {
			throw new ApiException(ErrorCode.SRV_005_E002);
		}
		return clean;
	}
}
