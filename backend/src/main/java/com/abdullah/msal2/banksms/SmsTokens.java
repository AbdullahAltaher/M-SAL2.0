package com.abdullah.msal2.banksms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The shortcut's personal tokens ({@code sms_tokens}, V9). Only the SHA-256 of a token is kept,
 * and a user has one active token at most (BANK-SMS.md 4.2).
 */
@Repository
public class SmsTokens {

	public static final String PREFIX = "msal_";
	private static final int RANDOM_LENGTH = 32;
	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	/** What the settings show about the active token: never the token itself. */
	public record Status(String hint, Instant createdAt, Instant lastUsedAt) {
	}

	private final JdbcTemplate jdbc;

	public SmsTokens(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** {@code msal_} and 32 random letters and digits. */
	static String newToken() {
		StringBuilder token = new StringBuilder(PREFIX);
		for (int i = 0; i < RANDOM_LENGTH; i++) {
			token.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return token.toString();
	}

	static String hash(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The user of an active token, noting that it was used now; empty for a wrong or revoked one. */
	public Optional<UUID> use(String token, Instant now) {
		if (token == null || !token.startsWith(PREFIX) || token.length() > 100) {
			return Optional.empty();
		}
		return jdbc.query("""
				update msal.sms_tokens set last_used_at = ?
				where token_hash = ? and revoked_at is null
				returning user_id
				""", (rs, i) -> rs.getObject(1, UUID.class), Timestamp.from(now), hash(token)).stream().findFirst();
	}

	/** A new token for the user; the old one stops working (B-19). Returns the token, shown once. */
	public String replace(UUID userId, Instant now) {
		revoke(userId, now);
		String token = newToken();
		jdbc.update("insert into msal.sms_tokens (user_id, token_hash, hint, created_at) values (?, ?, ?, ?)", userId,
				hash(token), hint(token), Timestamp.from(now));
		return token;
	}

	public int revoke(UUID userId, Instant now) {
		return jdbc.update("update msal.sms_tokens set revoked_at = ? where user_id = ? and revoked_at is null",
				Timestamp.from(now), userId);
	}

	public Optional<Status> active(UUID userId) {
		return jdbc.query("""
				select hint, created_at, last_used_at from msal.sms_tokens
				where user_id = ? and revoked_at is null
				""", (rs, i) -> new Status(rs.getString(1), rs.getTimestamp(2).toInstant(),
				rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()), userId).stream().findFirst();
	}

	static String hint(String token) {
		return token.substring(token.length() - 4);
	}
}
