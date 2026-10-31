package com.abdullah.msal2.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.abdullah.msal2.IntegrationTest;

/**
 * The daily reminder (SCREENS.md section 7): {@code dailyReminderTime} in {@code /me}, this
 * device's subscription, and the job. "Now" is 2026-11-11 08:00 UTC, 12:00 in Dubai.
 */
class PushApiTest extends IntegrationTest {

	private static final String APPLE = "https://web.push.apple.com/QGx8device";
	private static final String KEY = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";
	private static final String AUTH = "tBHItJI5svbpez7KI4CCXg";

	@Autowired
	DailyReminder reminder;

	@BeforeEach
	void pushWorks() {
		when(webPush.enabled()).thenReturn(true);
		when(webPush.send(anyString(), anyString(), anyString(), anyString())).thenReturn(WebPush.Outcome.SENT);
	}

	private static Map<String, Object> subscription(String endpoint) {
		return Map.of("endpoint", endpoint, "keys", Map.of("p256dh", KEY, "auth", AUTH), "expirationTime",
				"ignored");
	}

	private void remindAt(TestUser user, String time) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("dailyReminderTime", time);
		body.put("version", getJson(user, "/api/v1/me").get("version").asInt());
		send(user, patch("/api/v1/me"), body).andExpect(status().isOk());
	}

	private void subscribe(TestUser user, String endpoint) throws Exception {
		send(user, post("/api/v1/me/push-subscriptions"), subscription(endpoint)).andExpect(status().isNoContent());
	}

	private void addExpense(TestUser user, String date) throws Exception {
		send(user, post("/api/v1/budgets/" + budgetOf(user) + "/entries"),
				Map.of("amount", "5", "name", "قهوة", "categoryId", categoryOf(user, "cat_coffee"), "entryDate", date,
						"accountId", accountOf(user)))
				.andExpect(status().isCreated());
	}

	@Test
	void theReminderTimeIsSetAndClearedInMe() throws Exception {
		TestUser user = signUp("sara");
		call(user, get("/api/v1/me")).andExpect(jsonPath("$.dailyReminderTime").doesNotExist());
		remindAt(user, "21:30");
		call(user, get("/api/v1/me")).andExpect(jsonPath("$.dailyReminderTime").value("21:30:00"));
		remindAt(user, null);
		call(user, get("/api/v1/me")).andExpect(jsonPath("$.dailyReminderTime").doesNotExist());
		send(user, patch("/api/v1/me"), Map.of("dailyReminderTime", "25:00", "version", 2))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS-E006"));
	}

	@Test
	void aDeviceSubscribesOnceAndMovesToWhoeverSignsInOnIt() throws Exception {
		TestUser sara = signUp("sara");
		TestUser omar = signUp("omar");
		subscribe(sara, APPLE);
		subscribe(sara, APPLE);
		assertThat(jdbc.queryForObject("select count(*) from msal.push_subscriptions", Integer.class)).isEqualTo(1);

		subscribe(omar, APPLE);
		assertThat(jdbc.queryForObject("select user_id from msal.push_subscriptions", java.util.UUID.class))
				.isEqualTo(omar.id());

		// Sara cannot remove Omar's device
		call(sara, delete("/api/v1/me/push-subscriptions").param("endpoint", APPLE)).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from msal.push_subscriptions", Integer.class)).isEqualTo(1);
		call(omar, delete("/api/v1/me/push-subscriptions").param("endpoint", APPLE)).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from msal.push_subscriptions", Integer.class)).isZero();
	}

	@Test
	void onlyBrowserPushServicesAndRealKeysAreAccepted() throws Exception {
		TestUser user = signUp("sara");
		for (String endpoint : new String[] { "https://evil.example/push", "http://web.push.apple.com/x",
				"https://169.254.169.254/latest" }) {
			send(user, post("/api/v1/me/push-subscriptions"), subscription(endpoint))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SRV-000-E003"));
		}
		send(user, post("/api/v1/me/push-subscriptions"),
				Map.of("endpoint", APPLE, "keys", Map.of("p256dh", "x", "auth", AUTH)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SRV-000-E003"));
		send(user, post("/api/v1/me/push-subscriptions"), Map.of("endpoint", APPLE))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theReminderIsSentAtTheUsersOwnTimeOnceADay() throws Exception {
		TestUser sara = signUp("sara");
		subscribe(sara, APPLE);

		remindAt(sara, "12:30"); // Dubai is 12:00: not yet
		assertThat(reminder.run()).isZero();

		remindAt(sara, "11:15"); // 45 minutes ago: still sent
		assertThat(reminder.run()).isEqualTo(1);
		verify(webPush).send(eq(APPLE), eq(KEY), eq(AUTH), eq(DailyReminder.PAYLOAD));
		assertThat(reminder.run()).isZero(); // once a day
		assertThat(jdbc.queryForObject("select last_reminder_on::text from msal.app_users where id = ?", String.class,
				sara.id())).isEqualTo("2026-11-11");
		assertThat(jdbc.queryForObject("select last_used_at is not null from msal.push_subscriptions", Boolean.class))
				.isTrue();
	}

	@Test
	void aNewTimeTheSameDayRemindsAgain() throws Exception {
		TestUser sara = signUp("sara");
		subscribe(sara, APPLE);
		remindAt(sara, "11:30");
		assertThat(reminder.run()).isEqualTo(1);
		remindAt(sara, "11:30"); // the same time: nothing new
		assertThat(reminder.run()).isZero();
		remindAt(sara, "11:45");
		assertThat(reminder.run()).isEqualTo(1);
	}

	@Test
	void anHourLateOrInAnotherTimeZoneItIsNotSent() throws Exception {
		TestUser sara = signUp("sara");
		subscribe(sara, APPLE);
		remindAt(sara, "10:59"); // over an hour ago in Dubai
		assertThat(reminder.run()).isZero();

		TestUser omar = signUp("omar");
		subscribe(omar, "https://fcm.googleapis.com/fcm/send/omar");
		send(omar, patch("/api/v1/me"), Map.of("timeZone", "Europe/London", "version",
				getJson(omar, "/api/v1/me").get("version").asInt())).andExpect(status().isOk());
		remindAt(omar, "08:00"); // 08:00 in London now
		assertThat(reminder.run()).isEqualTo(1);
		verify(webPush, times(1)).send(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void anExpenseTodayMeansNoReminder() throws Exception {
		TestUser sara = signUp("sara");
		subscribe(sara, APPLE);
		addExpense(sara, "2026-11-11");
		remindAt(sara, "11:30");
		assertThat(reminder.run()).isZero();
		verify(webPush, never()).send(anyString(), anyString(), anyString(), anyString());

		// yesterday's expense does not count
		TestUser omar = signUp("omar");
		subscribe(omar, "https://fcm.googleapis.com/fcm/send/omar");
		addExpense(omar, "2026-11-10");
		remindAt(omar, "11:30");
		assertThat(reminder.run()).isEqualTo(1);
	}

	@Test
	void aDeviceThatUnsubscribedIsForgotten() throws Exception {
		TestUser sara = signUp("sara");
		subscribe(sara, APPLE);
		when(webPush.send(eq(APPLE), any(), any(), any())).thenReturn(WebPush.Outcome.GONE);
		remindAt(sara, "11:30");
		assertThat(reminder.run()).isZero();
		assertThat(jdbc.queryForObject("select count(*) from msal.push_subscriptions", Integer.class)).isZero();
	}

	@Test
	void meGivesThePublicKeyToSubscribeWith() throws Exception {
		when(webPush.publicKey()).thenReturn(KEY);
		TestUser sara = signUp("sara");
		call(sara, get("/api/v1/me")).andExpect(jsonPath("$.pushPublicKey").value(KEY));
	}
}
