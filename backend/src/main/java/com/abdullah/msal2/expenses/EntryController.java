package com.abdullah.msal2.expenses;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.cycles.CycleKey;
import com.abdullah.msal2.expenses.EntryDtos.CreateEntryRequest;
import com.abdullah.msal2.expenses.EntryDtos.DaysResponse;
import com.abdullah.msal2.expenses.EntryDtos.EntryResponse;
import com.abdullah.msal2.expenses.EntryDtos.HomeResponse;
import com.abdullah.msal2.expenses.EntryDtos.PurgeResponse;
import com.abdullah.msal2.expenses.EntryDtos.SuggestionsResponse;
import com.abdullah.msal2.expenses.EntryDtos.UpdateEntryRequest;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.time.Today;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-001: expenses (API.md section 4). {@code cycle} is {@code latest} (default), {@code none}
 * or a cycle id.
 */
@RestController
@ServiceCode("SRV-001")
public class EntryController {

	private final EntryService service;
	private final Clock clock;

	public EntryController(EntryService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@GetMapping("/accounts/{accountId}/home")
	public HomeResponse home(CurrentUser user, @PathVariable UUID accountId,
			@RequestParam(defaultValue = CycleKey.LATEST) String cycle) {
		return service.home(user.id(), accountId, cycle, Today.of(clock, user.timeZone()));
	}

	@GetMapping("/accounts/{accountId}/entries")
	public DaysResponse list(CurrentUser user, @PathVariable UUID accountId,
			@RequestParam(defaultValue = CycleKey.LATEST) String cycle, @RequestParam(required = false) String q,
			@RequestParam(required = false) UUID categoryId) {
		return service.list(user.id(), accountId, cycle, q, categoryId);
	}

	/** 204 when there is no expense yet. */
	@GetMapping("/accounts/{accountId}/entries/last-expense")
	public ResponseEntity<EntryResponse> lastExpense(CurrentUser user, @PathVariable UUID accountId) {
		return service.lastExpense(user.id(), accountId).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/budgets/{budgetId}/entries")
	@ResponseStatus(HttpStatus.CREATED)
	public EntryResponse create(CurrentUser user, @PathVariable UUID budgetId, @RequestBody CreateEntryRequest request) {
		return service.create(user.id(), budgetId, request);
	}

	@GetMapping("/entries/{entryId}")
	public EntryResponse get(CurrentUser user, @PathVariable UUID entryId) {
		return service.get(user.id(), entryId);
	}

	@PatchMapping("/entries/{entryId}")
	public EntryResponse update(CurrentUser user, @PathVariable UUID entryId, Patch<UpdateEntryRequest> patch) {
		return service.update(user.id(), entryId, patch);
	}

	@DeleteMapping("/entries/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(CurrentUser user, @PathVariable UUID entryId) {
		service.delete(user.id(), entryId);
	}

	@PostMapping("/entries/{entryId}/restore")
	public EntryResponse restore(CurrentUser user, @PathVariable UUID entryId) {
		return service.restore(user.id(), entryId);
	}

	@PostMapping("/entries/{entryId}/duplicate")
	@ResponseStatus(HttpStatus.CREATED)
	public EntryResponse duplicate(CurrentUser user, @PathVariable UUID entryId) {
		return service.duplicate(user.id(), entryId, Today.of(clock, user.timeZone()));
	}

	@DeleteMapping("/entries/{entryId}/permanent")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void purge(CurrentUser user, @PathVariable UUID entryId) {
		service.purge(user.id(), entryId);
	}

	@GetMapping("/budgets/{budgetId}/entries/deleted")
	public List<EntryResponse> deleted(CurrentUser user, @PathVariable UUID budgetId) {
		return service.deleted(user.id(), budgetId);
	}

	@DeleteMapping("/budgets/{budgetId}/entries/deleted")
	public PurgeResponse purgeDeleted(CurrentUser user, @PathVariable UUID budgetId) {
		return new PurgeResponse(service.purgeDeleted(user.id(), budgetId));
	}

	@GetMapping("/budgets/{budgetId}/entries/suggestions")
	public SuggestionsResponse suggestions(CurrentUser user, @PathVariable UUID budgetId, @RequestParam UUID categoryId) {
		return new SuggestionsResponse(service.suggestions(user.id(), budgetId, categoryId));
	}
}
