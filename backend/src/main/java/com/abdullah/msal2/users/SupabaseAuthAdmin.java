package com.abdullah.msal2.users;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.abdullah.msal2.system.MsalProperties;

import tools.jackson.databind.JsonNode;

/**
 * Supabase Auth Admin API, with the server-only secret key (API.md 2.3). Invited users are
 * created confirmed and without a password; no email is sent.
 */
@Component
public class SupabaseAuthAdmin {

	private static final Logger log = LoggerFactory.getLogger(SupabaseAuthAdmin.class);

	/** Supabase could not do it; the caller answers with {@code SRV-009-E004}. */
	public static class AuthAdminException extends RuntimeException {

		public AuthAdminException(String message) {
			super(message);
		}
	}

	private final RestClient client;
	private final boolean configured;

	public SupabaseAuthAdmin(MsalProperties properties) {
		String key = properties.supabaseSecretKey();
		this.configured = key != null && !key.isBlank();
		this.client = RestClient.builder().baseUrl(properties.supabaseUrl() + "/auth/v1/admin")
				.defaultHeader("apikey", configured ? key : "")
				.defaultHeader("Authorization", "Bearer " + (configured ? key : ""))
				.build();
	}

	/**
	 * Creates a confirmed user without a password and returns its id, or null when the email
	 * already has a Supabase user.
	 */
	public UUID createUser(String email) {
		requireConfigured();
		try {
			JsonNode user = client.post().uri("/users")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("email", email, "email_confirm", true))
					.retrieve()
					.onStatus(status -> status.value() == 422, (request, response) -> {
						// Already registered in Supabase Auth: the invitation still works
					})
					.body(JsonNode.class);
			return user != null && user.hasNonNull("id") ? UUID.fromString(user.get("id").asString()) : null;
		}
		catch (RestClientException e) {
			log.warn("Supabase createUser failed: {}", e.getClass().getSimpleName());
			throw new AuthAdminException("createUser failed");
		}
	}

	/** Deletes a Supabase user that never signed in (a revoked invitation). */
	public void deleteUser(UUID id) {
		requireConfigured();
		try {
			client.delete().uri("/users/{id}", id).retrieve()
					.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
						// Already gone
					})
					.toBodilessEntity();
		}
		catch (RestClientException e) {
			log.warn("Supabase deleteUser failed: {}", e.getClass().getSimpleName());
			throw new AuthAdminException("deleteUser failed");
		}
	}

	private void requireConfigured() {
		if (!configured) {
			throw new AuthAdminException("SUPABASE_SECRET_KEY is not set");
		}
	}
}
