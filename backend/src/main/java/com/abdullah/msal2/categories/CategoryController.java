package com.abdullah.msal2.categories;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.categories.CategoryDtos.CategoryResponse;
import com.abdullah.msal2.categories.CategoryDtos.CreateCategoryRequest;
import com.abdullah.msal2.categories.CategoryDtos.OrderRequest;
import com.abdullah.msal2.categories.CategoryDtos.UpdateCategoryRequest;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-005: categories. There is no DELETE: categories are only hidden.
 */
@RestController
@ServiceCode("SRV-005")
public class CategoryController {

	private final CategoryService service;

	public CategoryController(CategoryService service) {
		this.service = service;
	}

	@GetMapping("/budgets/{budgetId}/categories")
	public List<CategoryResponse> list(CurrentUser user, @PathVariable UUID budgetId) {
		return service.list(user.id(), budgetId);
	}

	@PostMapping("/budgets/{budgetId}/categories")
	@ResponseStatus(HttpStatus.CREATED)
	public CategoryResponse create(CurrentUser user, @PathVariable UUID budgetId,
			@Valid @RequestBody CreateCategoryRequest request) {
		return service.create(user.id(), budgetId, request);
	}

	@PatchMapping("/categories/{categoryId}")
	public CategoryResponse update(CurrentUser user, @PathVariable UUID categoryId, Patch<UpdateCategoryRequest> patch) {
		return service.update(user.id(), categoryId, patch);
	}

	@PutMapping("/budgets/{budgetId}/categories/order")
	public List<CategoryResponse> reorder(CurrentUser user, @PathVariable UUID budgetId,
			@Valid @RequestBody OrderRequest request) {
		return service.reorder(user.id(), budgetId, request.categoryIds());
	}
}
