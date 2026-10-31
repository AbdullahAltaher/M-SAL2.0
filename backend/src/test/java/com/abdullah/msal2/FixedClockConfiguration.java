package com.abdullah.msal2;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Fixes "now" for the integration tests at {@link #NOW}: 2026-11-11 08:00 UTC, 12:00 in Dubai,
 * the date of the examples in BUSINESS_RULES.md.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

	public static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

	@Bean
	@Primary
	Clock testClock() {
		return Clock.fixed(NOW, ZoneOffset.UTC);
	}
}
