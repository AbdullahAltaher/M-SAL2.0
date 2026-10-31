package com.abdullah.msal2.system.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.abdullah.msal2.system.MsalProperties;
import com.abdullah.msal2.system.error.ApiErrorWriter;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * Supabase JWT verification (API.md 2.6) and CORS (API.md section 7).
 * <p>
 * The project signs tokens with ES256, so tokens are checked against the public JWKS:
 * signature, expiry, {@code iss} and {@code aud = authenticated}. Who the user is and what
 * they may do is decided after this, by the access interceptor.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	static final String AUDIENCE = "authenticated";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter errors) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> {
				})
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.requestCache(cache -> cache.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
						.requestMatchers("/api/**").authenticated()
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> {
						})
						.authenticationEntryPoint((request, response, e) -> errors.write(response, ErrorCode.SYS_E001)))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, e) -> errors.write(response, ErrorCode.SYS_E001))
						.accessDeniedHandler((request, response, e) -> errors.write(response, ErrorCode.SYS_E002)));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(MsalProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
				.jwsAlgorithm(SignatureAlgorithm.ES256)
				.build();
		decoder.setJwtValidator(jwtValidator(properties.jwtIssuer()));
		return decoder;
	}

	/** Expiry and issuer (the defaults) plus the Supabase audience. */
	static OAuth2TokenValidator<Jwt> jwtValidator(String issuer) {
		OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
		return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(MsalProperties properties) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(properties.corsOrigins());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cors.setExposedHeaders(List.of("X-Request-Id"));
		cors.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}
}
