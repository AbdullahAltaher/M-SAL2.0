package com.abdullah.msal2.users;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SRV-009 requests and responses (API.md section 4). The admin never sees anyone's financial
 * data: users, roles, invitations, services and the activity log without its data columns.
 */
public final class AdminDtos {

	private AdminDtos() {
	}

	public record UserResponse(UUID id, String email, String displayName, UserStatus status, List<String> roles,
			Instant createdAt, int version) {

		static UserResponse of(AppUser u) {
			return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getStatus(),
					u.getRoles().stream().sorted().toList(), u.getCreatedAt(), u.getVersion());
		}
	}

	public record UpdateUserRequest(UserStatus status, Set<String> roles, @NotNull Integer version) {
	}

	public record InvitationResponse(UUID id, String email, String roleCode, InvitationStatus status, UUID invitedBy,
			Instant expiresAt, Instant acceptedAt, Instant createdAt) {
	}

	/** The email is trimmed and lower-cased before it is checked. */
	public record InviteRequest(@NotBlank @Size(max = 254) String email, String roleCode) {
	}

	public record ServiceResponse(String code, String nameAr, String nameEn, boolean enabled) {
	}

	public record UpdateServiceRequest(@NotNull Boolean enabled) {
	}

	public record AuditEntry(long id, Instant occurredAt, UUID userId, String userEmail, String serviceCode,
			String action, String entityType, UUID entityId, String requestId) {
	}

	public record AuditPage(List<AuditEntry> items, int page, int size, long total) {
	}
}
