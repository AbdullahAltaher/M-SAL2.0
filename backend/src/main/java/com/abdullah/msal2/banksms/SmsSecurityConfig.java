package com.abdullah.msal2.banksms;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.abdullah.msal2.system.WebConfig;
import com.abdullah.msal2.system.error.ApiErrorWriter;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * The shortcut's endpoint has its own security chain (BANK-SMS.md 4.3): no JWT and no CORS (the
 * shortcut is not a browser), only the personal token {@code Authorization: Bearer msal_...},
 * looked up by its SHA-256. A wrong or revoked token is {@code SYS-E001}. The rest of the access
 * check (the user, the service, the request limit) is the usual interceptor's.
 */
@Configuration(proxyBeanMethods = false)
public class SmsSecurityConfig {

	static final String INGEST_PATH = "/sms/ingest";
	public static final String INGEST_URL = WebConfig.API_PREFIX + INGEST_PATH;

	/** Who sent the message: the token's user. */
	public static final class SmsTokenAuthentication extends AbstractAuthenticationToken {

		private final UUID userId;

		SmsTokenAuthentication(UUID userId) {
			super(List.of());
			this.userId = userId;
			setAuthenticated(true);
		}

		@Override
		public Object getCredentials() {
			return null;
		}

		@Override
		public UUID getPrincipal() {
			return userId;
		}
	}

	/** Before the app's chain, which would ask for a JWT. */
	@Bean
	@Order(1)
	SecurityFilterChain smsIngestChain(HttpSecurity http, SmsTokens tokens, Clock clock, ApiErrorWriter errors)
			throws Exception {
		http
				.securityMatcher(INGEST_URL)
				.csrf(csrf -> csrf.disable())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.requestCache(cache -> cache.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Not a bean: as a bean, Spring Boot would also run it on every request
				.addFilterBefore(new TokenFilter(tokens, clock), AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, e) -> errors.write(response, ErrorCode.SYS_E001))
						.accessDeniedHandler((request, response, e) -> errors.write(response, ErrorCode.SYS_E002)));
		return http.build();
	}

	/** Signs the request in as the token's user; without a valid token it stays anonymous (401). */
	static final class TokenFilter extends OncePerRequestFilter {

		private static final String BEARER = "Bearer ";

		private final SmsTokens tokens;
		private final Clock clock;

		TokenFilter(SmsTokens tokens, Clock clock) {
			this.tokens = tokens;
			this.clock = clock;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			// Only the token signs in here: whatever else the request carries is dropped
			SecurityContextHolder.clearContext();
			String header = request.getHeader(HttpHeaders.AUTHORIZATION);
			if (header != null && header.startsWith(BEARER)) {
				tokens.use(header.substring(BEARER.length()).strip(), Instant.now(clock)).ifPresent(userId -> {
					SecurityContext context = SecurityContextHolder.createEmptyContext();
					context.setAuthentication(new SmsTokenAuthentication(userId));
					SecurityContextHolder.setContext(context);
				});
			}
			chain.doFilter(request, response);
		}
	}
}
