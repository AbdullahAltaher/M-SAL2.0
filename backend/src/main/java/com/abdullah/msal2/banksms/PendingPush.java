package com.abdullah.msal2.banksms;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.abdullah.msal2.push.UserPush;

import tools.jackson.databind.json.JsonMapper;

/**
 * A new message waiting for review tells the user's devices (BANK-SMS.md B-22): the number on
 * the app icon becomes all their messages waiting for review, and the notification (which the
 * owner may hide in the iPhone's settings, keeping only the number) opens the review page. Each
 * one replaces the last ({@code tag}), so they do not pile up. Sent after the entry is saved,
 * and a failure never changes the shortcut's answer. The user can turn it off in the settings
 * ({@code bank_sms_push}, D-41).
 */
@Component
public class PendingPush {

	private static final Logger log = LoggerFactory.getLogger(PendingPush.class);

	private final UserPush push;
	private final PendingEntries pending;
	private final JsonMapper json;
	private final JdbcTemplate jdbc;

	public PendingPush(UserPush push, PendingEntries pending, JsonMapper json, JdbcTemplate jdbc) {
		this.push = push;
		this.pending = pending;
		this.json = json;
		this.jdbc = jdbc;
	}

	public void newPending(UUID userId, String line) {
		if (!push.enabled()) {
			return;
		}
		try {
			if (!Boolean.TRUE.equals(jdbc.queryForObject(
					"select bank_sms_push from msal.app_users where id = ?", Boolean.class, userId))) {
				return;
			}
			push.send(userId, payload(line, pending.countOfUser(userId)), "Bank message");
		} catch (RuntimeException e) {
			log.warn("Bank message notification failed for user {}", userId, e);
		}
	}

	String payload(String line, int badge) {
		Map<String, Object> notification = new LinkedHashMap<>();
		notification.put("title", "ميزانية");
		notification.put("body", line);
		notification.put("icon", "icons/icon-192.png");
		notification.put("lang", "ar");
		notification.put("dir", "rtl");
		notification.put("tag", "bank-sms");
		notification.put("data", Map.of("badge", badge, "onActionClick",
				Map.of("default", Map.of("operation", "navigateLastFocusedOrOpen", "url", "expenses/pending"))));
		return json.writeValueAsString(Map.of("notification", notification));
	}
}
