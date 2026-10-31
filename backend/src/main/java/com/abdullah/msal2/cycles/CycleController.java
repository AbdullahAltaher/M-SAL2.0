package com.abdullah.msal2.cycles;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.cycles.CycleDtos.CreateCycleRequest;
import com.abdullah.msal2.cycles.CycleDtos.CycleResponse;
import com.abdullah.msal2.cycles.CycleDtos.CyclesResponse;
import com.abdullah.msal2.cycles.CycleDtos.MoveImpactResponse;
import com.abdullah.msal2.cycles.CycleDtos.PrefillResponse;
import com.abdullah.msal2.cycles.CycleDtos.UpdateCycleRequest;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.time.Today;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-003: salary cycles (API.md section 4).
 */
@RestController
@ServiceCode("SRV-003")
public class CycleController {

	private final CycleService service;
	private final Clock clock;

	public CycleController(CycleService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@GetMapping("/accounts/{accountId}/cycles")
	public CyclesResponse list(CurrentUser user, @PathVariable UUID accountId) {
		return service.list(user.id(), accountId);
	}

	@GetMapping("/accounts/{accountId}/cycles/prefill")
	public PrefillResponse prefill(CurrentUser user, @PathVariable UUID accountId) {
		return service.prefill(user.id(), accountId, Today.of(clock, user.timeZone()));
	}

	@PostMapping("/accounts/{accountId}/cycles")
	@ResponseStatus(HttpStatus.CREATED)
	public CycleResponse create(CurrentUser user, @PathVariable UUID accountId, @RequestBody CreateCycleRequest request) {
		return service.create(user.id(), accountId, request);
	}

	@PatchMapping("/salary-cycles/{cycleId}")
	public CycleResponse update(CurrentUser user, @PathVariable UUID cycleId, Patch<UpdateCycleRequest> patch) {
		return service.update(user.id(), cycleId, patch);
	}

	@GetMapping("/salary-cycles/{cycleId}/move-impact")
	public MoveImpactResponse moveImpact(CurrentUser user, @PathVariable UUID cycleId, @RequestParam String startDate) {
		return new MoveImpactResponse(service.moveImpact(user.id(), cycleId, startDate));
	}

	@DeleteMapping("/salary-cycles/{cycleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(CurrentUser user, @PathVariable UUID cycleId) {
		service.delete(user.id(), cycleId);
	}
}
