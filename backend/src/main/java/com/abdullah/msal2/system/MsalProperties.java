package com.abdullah.msal2.system;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code msal.*} in application.yml.
 *
 * @param supabaseUrl         the Supabase project URL, used for the JWT issuer and JWKS
 * @param supabaseSecretKey   server-only key for the Auth Admin API (invitations, API.md 2.3); never logged
 * @param bootstrapAdminEmail the email that becomes ADMIN on the first sign-in (API.md 2.5)
 * @param corsOrigins         origins allowed to call the API (API.md section 7)
 */
@ConfigurationProperties("msal")
public record MsalProperties(String supabaseUrl, String supabaseSecretKey, String bootstrapAdminEmail,
		List<String> corsOrigins) {

	public String jwtIssuer() {
		return supabaseUrl + "/auth/v1";
	}

	public String jwkSetUri() {
		return supabaseUrl + "/auth/v1/.well-known/jwks.json";
	}

	/** Never prints the secret key. */
	@Override
	public String toString() {
		return "MsalProperties[supabaseUrl=" + supabaseUrl + ", supabaseSecretKey="
				+ (supabaseSecretKey == null || supabaseSecretKey.isBlank() ? "<unset>" : "<set>") + ", corsOrigins="
				+ corsOrigins + "]";
	}
}
