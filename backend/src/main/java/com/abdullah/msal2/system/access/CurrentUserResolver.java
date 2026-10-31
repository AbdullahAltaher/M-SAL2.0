package com.abdullah.msal2.system.access;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Finds the app user for a verified JWT, creating it on the first sign-in when allowed
 * (API.md 2.4). Throws {@code SRV-000-E001} for an email that is not invited.
 */
public interface CurrentUserResolver {

	CurrentUser resolve(Jwt jwt);
}
