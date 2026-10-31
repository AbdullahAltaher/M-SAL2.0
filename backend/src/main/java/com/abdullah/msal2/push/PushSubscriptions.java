package com.abdullah.msal2.push;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * The devices a user gets notifications on ({@code push_subscriptions}, V8). A browser's
 * endpoint belongs to whoever signed in on it last.
 */
@Repository
public class PushSubscriptions {

	/**
	 * The server posts to the endpoint, so only the browsers' own push services are allowed:
	 * Apple (Safari and the iPhone), Google (Chrome), Mozilla (Firefox) and Microsoft (Edge).
	 */
	private static final Pattern PUSH_HOSTS = Pattern.compile(
			"(web\\.push\\.apple\\.com|[a-z0-9.-]+\\.push\\.apple\\.com|fcm\\.googleapis\\.com"
					+ "|updates\\.push\\.services\\.mozilla\\.com|[a-z0-9.-]+\\.notify\\.windows\\.com)");
	private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{16,200}={0,2}");

	public record Subscription(UUID id, String endpoint, String p256dh, String auth) {
	}

	private final JdbcTemplate jdbc;
	private final Clock clock;

	public PushSubscriptions(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional
	public void save(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
		if (!validEndpoint(endpoint) || p256dh == null || auth == null || !KEY.matcher(p256dh).matches()
				|| !KEY.matcher(auth).matches()) {
			throw new ApiException(ErrorCode.SRV_000_E003);
		}
		jdbc.update("""
				insert into msal.push_subscriptions (user_id, endpoint, p256dh, auth, user_agent, created_at)
				values (?, ?, ?, ?, ?, ?)
				on conflict (endpoint) do update
				set user_id = excluded.user_id, p256dh = excluded.p256dh, auth = excluded.auth,
				    user_agent = excluded.user_agent
				""", userId, endpoint, p256dh, auth, userAgent == null ? null : userAgent.substring(0,
				Math.min(userAgent.length(), 300)), Timestamp.from(Instant.now(clock)));
	}

	/** Only the user's own device; another user's endpoint is left alone. */
	@Transactional
	public void delete(UUID userId, String endpoint) {
		jdbc.update("delete from msal.push_subscriptions where user_id = ? and endpoint = ?", userId, endpoint);
	}

	public List<Subscription> of(UUID userId) {
		return jdbc.query("select id, endpoint, p256dh, auth from msal.push_subscriptions where user_id = ?",
				(rs, n) -> new Subscription(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
						rs.getString(4)),
				userId);
	}

	public void used(UUID id) {
		jdbc.update("update msal.push_subscriptions set last_used_at = ? where id = ?",
				Timestamp.from(Instant.now(clock)), id);
	}

	public void gone(UUID id) {
		jdbc.update("delete from msal.push_subscriptions where id = ?", id);
	}

	static boolean validEndpoint(String endpoint) {
		if (endpoint == null || endpoint.length() > 1000) {
			return false;
		}
		try {
			URI uri = new URI(endpoint);
			return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getPort() == -1
					&& uri.getUserInfo() == null && PUSH_HOSTS.matcher(uri.getHost().toLowerCase()).matches();
		}
		catch (Exception e) {
			return false;
		}
	}
}
