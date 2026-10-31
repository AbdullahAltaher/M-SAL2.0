package com.abdullah.msal2.users;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.abdullah.msal2.system.persistence.TimestampedEntity;

/**
 * An invitation to use the app (SRV-009). Only invited emails can sign in (CLAUDE.md rule 12).
 */
@Entity
@Table(name = "invitations")
public class Invitation extends TimestampedEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String email;

	@Column(name = "role_code", nullable = false)
	private String roleCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InvitationStatus status;

	@Column(name = "invited_by", nullable = false)
	private UUID invitedBy;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	protected Invitation() {
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getRoleCode() {
		return roleCode;
	}

	public InvitationStatus getStatus() {
		return status;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public void accept(Instant now) {
		status = InvitationStatus.ACCEPTED;
		acceptedAt = now;
	}
}
