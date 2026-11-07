package com.abdullah.msal2.banksms;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.abdullah.msal2.categories.Category;
import com.abdullah.msal2.categories.CategoryService;

/**
 * The category suggested for a merchant in a budget, from the budget's words
 * ({@link MerchantClassifier}). A word of a hidden category is skipped, so another word can win
 * (BANK-SMS.md 4.2).
 */
@Component
public class CategorySuggester {

	/**
	 * @param category    the suggested category, if any
	 * @param askEachTime the winning word is one whose category is picked every time (B-15)
	 */
	public record Suggestion(Optional<Category> category, boolean askEachTime) {

		static final Suggestion NONE = new Suggestion(Optional.empty(), false);
	}

	private final MerchantKeywords keywords;
	private final CategoryService categories;

	public CategorySuggester(MerchantKeywords keywords, CategoryService categories) {
		this.keywords = keywords;
		this.categories = categories;
	}

	/** Loads the budget's words and categories once, for any number of merchants. */
	public Function<String, Suggestion> forBudget(UUID budgetId) {
		List<Category> visible = categories.inBudget(budgetId).stream().filter(c -> !c.isHidden()).toList();
		Set<UUID> visibleIds = visible.stream().map(Category::getId).collect(Collectors.toSet());
		List<MerchantClassifier.Keyword> words = keywords.ofBudget(budgetId).stream()
				.filter(k -> k.categoryId() == null || visibleIds.contains(k.categoryId()))
				.map(k -> new MerchantClassifier.Keyword(k.keyword(), k.categoryId()))
				.toList();
		return merchant -> MerchantClassifier.match(merchant, words)
				.map(k -> k.categoryId() == null ? new Suggestion(Optional.empty(), true)
						: new Suggestion(visible.stream().filter(c -> c.getId().equals(k.categoryId())).findFirst(), false))
				.orElse(Suggestion.NONE);
	}
}
