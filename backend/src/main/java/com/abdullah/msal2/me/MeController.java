package com.abdullah.msal2.me;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;

/**
 * SRV-000: the signed-in user's account and session (API.md section 4).
 * The first {@code GET /me} creates the user (API.md 2.4).
 */
@RestController
@ServiceCode("SRV-000")
@RequestMapping("/me")
public class MeController {

	private final MeService service;

	public MeController(MeService service) {
		this.service = service;
	}

	@GetMapping
	public MeResponse get(CurrentUser user) {
		return service.get(user);
	}

	@PatchMapping
	public MeResponse update(CurrentUser user, @Valid @RequestBody UpdateMeRequest request) {
		return service.update(user, request);
	}
}
