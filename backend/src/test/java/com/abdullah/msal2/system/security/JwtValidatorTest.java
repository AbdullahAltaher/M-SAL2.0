package com.abdullah.msal2.system.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The claims checked on every Supabase token (API.md 2.6). The signature is checked by the
 * JWKS decoder and is not part of this test.
 */
class JwtValidatorTest {

	private static final String ISSUER = "https://example.supabase.co/auth/v1";

	private final OAuth2TokenValidator<Jwt> validator = SecurityConfig.jwtValidator(ISSUER);

	private static Jwt token(String issuer, String audience, Instant expiresAt) {
		return Jwt.withTokenValue("token")
				.header("alg", "ES256")
				.issuer(issuer)
				.audience(List.of(audience))
				.subject("3f2a0000-0000-0000-0000-000000000000")
				.issuedAt(expiresAt.minusSeconds(3600))
				.expiresAt(expiresAt)
				.build();
	}

	@Test
	void acceptsValidToken() {
		assertThat(validator.validate(token(ISSUER, "authenticated", Instant.now().plusSeconds(600))).hasErrors())
				.isFalse();
	}

	@Test
	void rejectsOtherIssuer() {
		assertThat(validator.validate(token("https://other.supabase.co/auth/v1", "authenticated",
				Instant.now().plusSeconds(600))).hasErrors()).isTrue();
	}

	@Test
	void rejectsOtherAudience() {
		assertThat(validator.validate(token(ISSUER, "anon", Instant.now().plusSeconds(600))).hasErrors()).isTrue();
	}

	@Test
	void rejectsExpiredToken() {
		assertThat(validator.validate(token(ISSUER, "authenticated", Instant.now().minusSeconds(600))).hasErrors())
				.isTrue();
	}
}
