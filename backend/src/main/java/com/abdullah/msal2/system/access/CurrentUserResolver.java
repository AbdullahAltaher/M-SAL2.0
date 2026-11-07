package com.abdullah.msal2.system.access;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Finds the app user for a verified JWT, creating it on the first sign-in when allowed
 * (API.md 2.4). Throws {@code SRV-000-E001} for an email that is not invited.
 */
public interface CurrentUserResolver {

	CurrentUser resolve(Jwt jwt);

	/**
	 * An existing user by id, for requests signed in another way than a JWT (the bank shortcut's
	 * token, BANK-SMS.md 4.3). Never creates one: an unknown id is {@code SYS-E001}.
	 */
	CurrentUser resolve(UUID userId);
}
