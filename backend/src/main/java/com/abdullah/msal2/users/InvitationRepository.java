package com.abdullah.msal2.users;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	/** At most one pending invitation per email (unique index {@code ux_invitations_pending_email}). */
	Optional<Invitation> findFirstByEmailIgnoreCaseAndStatus(String email, InvitationStatus status);
}
