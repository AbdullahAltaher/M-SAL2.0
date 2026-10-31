package com.abdullah.msal2.system;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Application-wide beans: the clock, JSON settings and JPA auditing.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MsalProperties.class)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class AppConfig {

	/** "Now" comes from this clock, so tests can fix any date (API.md section 8, note 5). */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/** {@code created_at} and {@code updated_at} use the same clock. */
	@Bean
	DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(Instant.now(clock));
	}

	/** Amounts are strings in JSON ({@code "46.25"}), so no cents are lost in JavaScript (CLAUDE.md rule 6). */
	@Bean
	JsonMapperBuilderCustomizer bigDecimalAsString() {
		return builder -> builder.withConfigOverride(BigDecimal.class,
				override -> override.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)));
	}
}
