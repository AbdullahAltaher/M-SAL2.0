package com.abdullah.msal2.system;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code msal.*} in application.yml.
 *
 * @param supabaseUrl         the Supabase project URL, used for the JWT issuer and JWKS
 * @param bootstrapAdminEmail the email that becomes ADMIN on the first sign-in (API.md 2.5)
 * @param corsOrigins         origins allowed to call the API (API.md section 7)
 */
@ConfigurationProperties("msal")
public record MsalProperties(String supabaseUrl, String bootstrapAdminEmail, List<String> corsOrigins) {

	public String jwtIssuer() {
		return supabaseUrl + "/auth/v1";
	}

	public String jwkSetUri() {
		return supabaseUrl + "/auth/v1/.well-known/jwks.json";
	}
}
