package com.abdullah.msal2.budget;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.budget.BudgetDtos.BudgetListItem;
import com.abdullah.msal2.budget.BudgetDtos.BudgetResponse;
import com.abdullah.msal2.budget.BudgetDtos.CreateBudgetRequest;
import com.abdullah.msal2.budget.BudgetDtos.CreateGroupRequest;
import com.abdullah.msal2.budget.BudgetDtos.GroupResponse;
import com.abdullah.msal2.budget.BudgetDtos.ItemResponse;
import com.abdullah.msal2.budget.BudgetDtos.UpdateBudgetRequest;
import com.abdullah.msal2.budget.BudgetDtos.UpdateGroupRequest;
import com.abdullah.msal2.budget.BudgetDtos.UpdateItemRequest;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.time.Today;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-002: the monthly budget (API.md section 4).
 */
@RestController
@ServiceCode("SRV-002")
public class BudgetController {

	private final BudgetService service;
	private final Clock clock;

	public BudgetController(BudgetService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@GetMapping("/budgets")
	public List<BudgetListItem> list(CurrentUser user) {
		return service.list(user.id());
	}

	@PostMapping("/budgets")
	@ResponseStatus(HttpStatus.CREATED)
	public BudgetResponse create(CurrentUser user, @Valid @RequestBody CreateBudgetRequest request) {
		return service.create(user.id(), request.name(), Today.of(clock, user.timeZone()));
	}

	@GetMapping("/budgets/{budgetId}")
	public BudgetResponse get(CurrentUser user, @PathVariable UUID budgetId) {
		return service.get(user.id(), budgetId);
	}

	@PatchMapping("/budgets/{budgetId}")
	public BudgetResponse update(CurrentUser user, @PathVariable UUID budgetId, Patch<UpdateBudgetRequest> patch) {
		return service.update(user.id(), budgetId, patch);
	}

	@DeleteMapping("/budgets/{budgetId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(CurrentUser user, @PathVariable UUID budgetId) {
		service.delete(user.id(), budgetId);
	}

	@PostMapping("/budgets/{budgetId}/new-month")
	public BudgetResponse newMonth(CurrentUser user, @PathVariable UUID budgetId) {
		return service.newMonth(user.id(), budgetId, Today.of(clock, user.timeZone()));
	}

	@PostMapping("/budgets/{budgetId}/groups")
	@ResponseStatus(HttpStatus.CREATED)
	public GroupResponse addGroup(CurrentUser user, @PathVariable UUID budgetId,
			@Valid @RequestBody(required = false) CreateGroupRequest request) {
		return service.addGroup(user.id(), budgetId, request == null ? "" : request.title());
	}

	@PatchMapping("/budget-groups/{groupId}")
	public GroupResponse updateGroup(CurrentUser user, @PathVariable UUID groupId, Patch<UpdateGroupRequest> patch) {
		return service.updateGroup(user.id(), groupId, patch);
	}

	@DeleteMapping("/budget-groups/{groupId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteGroup(CurrentUser user, @PathVariable UUID groupId) {
		service.deleteGroup(user.id(), groupId);
	}

	@PostMapping("/budget-groups/{groupId}/items")
	@ResponseStatus(HttpStatus.CREATED)
	public ItemResponse addItem(CurrentUser user, @PathVariable UUID groupId) {
		return service.addItem(user.id(), groupId);
	}

	@PatchMapping("/budget-items/{itemId}")
	public ItemResponse updateItem(CurrentUser user, @PathVariable UUID itemId, Patch<UpdateItemRequest> patch) {
		return service.updateItem(user.id(), itemId, patch);
	}

	@DeleteMapping("/budget-items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteItem(CurrentUser user, @PathVariable UUID itemId) {
		service.deleteItem(user.id(), itemId);
	}
}
