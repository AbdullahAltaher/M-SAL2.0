package com.abdullah.msal2.users;

import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.access.StrictRateLimit;
import com.abdullah.msal2.system.web.Patch;
import com.abdullah.msal2.users.AdminDtos.AuditPage;
import com.abdullah.msal2.users.AdminDtos.InvitationResponse;
import com.abdullah.msal2.users.AdminDtos.InviteRequest;
import com.abdullah.msal2.users.AdminDtos.ServiceResponse;
import com.abdullah.msal2.users.AdminDtos.UpdateServiceRequest;
import com.abdullah.msal2.users.AdminDtos.UpdateUserRequest;
import com.abdullah.msal2.users.AdminDtos.UserResponse;

/**
 * SRV-009: user and invitation management. Only the ADMIN role has this service (V2__seed_system.sql).
 */
@RestController
@ServiceCode("SRV-009")
@RequestMapping("/admin")
public class AdminController {

	private final AdminService service;

	public AdminController(AdminService service) {
		this.service = service;
	}

	@GetMapping("/users")
	public List<UserResponse> users() {
		return service.users();
	}

	@PatchMapping("/users/{userId}")
	public UserResponse updateUser(CurrentUser admin, @PathVariable UUID userId, Patch<UpdateUserRequest> patch) {
		return service.updateUser(admin.id(), userId, patch);
	}

	@GetMapping("/invitations")
	public List<InvitationResponse> invitations() {
		return service.invitations();
	}

	@PostMapping("/invitations")
	@StrictRateLimit
	@ResponseStatus(HttpStatus.CREATED)
	public InvitationResponse invite(CurrentUser admin, @Valid @RequestBody InviteRequest request) {
		return service.invite(admin.id(), request.email(), request.roleCode());
	}

	@DeleteMapping("/invitations/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(CurrentUser admin, @PathVariable UUID invitationId) {
		service.revoke(admin.id(), invitationId);
	}

	@GetMapping("/services")
	public List<ServiceResponse> services() {
		return service.services();
	}

	@PatchMapping("/services/{code}")
	public ServiceResponse updateService(CurrentUser admin, @PathVariable String code,
			@Valid @RequestBody UpdateServiceRequest request) {
		return service.updateService(admin.id(), code, request.enabled());
	}

	@GetMapping("/audit-log")
	public AuditPage auditLog(@RequestParam(required = false) UUID userId,
			@RequestParam(required = false) String service, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return this.service.auditLog(userId, service, from, to, page, size);
	}
}
