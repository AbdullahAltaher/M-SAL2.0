package com.abdullah.msal2.system.access;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Requests per user per minute (API.md section 7): 120 in general, and 10 for endpoints marked
 * {@link StrictRateLimit} (invitations, and imports in phase 6). Over the limit is {@code SYS-E007}.
 * <p>
 * In memory with one fixed window per user, which fits a single server.
 */
@Component
public class RateLimiter {

	private record Window(long minute, int count) {
	}

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
	private final Clock clock;
	private final int perMinute;
	private final int strictPerMinute;

	public RateLimiter(Clock clock, @Value("${msal.rate-limit.per-minute:120}") int perMinute,
			@Value("${msal.rate-limit.strict-per-minute:10}") int strictPerMinute) {
		this.clock = clock;
		this.perMinute = perMinute;
		this.strictPerMinute = strictPerMinute;
	}

	/** Counts the request and answers whether it is within the limit. */
	public boolean tryAcquire(UUID userId, boolean strict) {
		boolean general = acquire(userId.toString(), perMinute);
		return strict ? acquire(userId + ":strict", strictPerMinute) && general : general;
	}

	private boolean acquire(String key, int limit) {
		long minute = clock.millis() / 60_000;
		Window window = windows.compute(key,
				(k, old) -> old == null || old.minute() != minute ? new Window(minute, 1) : new Window(minute, old.count() + 1));
		return window.count() <= limit;
	}
}
