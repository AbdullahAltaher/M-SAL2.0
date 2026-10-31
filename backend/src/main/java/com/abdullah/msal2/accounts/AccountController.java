package com.abdullah.msal2.accounts;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.accounts.AccountDtos.AccountResponse;
import com.abdullah.msal2.accounts.AccountDtos.CreateAccountRequest;
import com.abdullah.msal2.accounts.AccountDtos.UpdateAccountRequest;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-004: accounts.
 */
@RestController
@ServiceCode("SRV-004")
public class AccountController {

	private final AccountService service;

	public AccountController(AccountService service) {
		this.service = service;
	}

	@GetMapping("/budgets/{budgetId}/accounts")
	public List<AccountResponse> list(CurrentUser user, @PathVariable UUID budgetId,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(user.id(), budgetId, includeArchived);
	}

	@PostMapping("/budgets/{budgetId}/accounts")
	@ResponseStatus(HttpStatus.CREATED)
	public AccountResponse create(CurrentUser user, @PathVariable UUID budgetId,
			@Valid @RequestBody CreateAccountRequest request) {
		return service.create(user.id(), budgetId, request.name());
	}

	@PatchMapping("/accounts/{accountId}")
	public AccountResponse update(CurrentUser user, @PathVariable UUID accountId, Patch<UpdateAccountRequest> patch) {
		return service.update(user.id(), accountId, patch);
	}
}
