package com.abdullah.msal2.banksms;

import static com.abdullah.msal2.banksms.FakeSms.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.abdullah.msal2.IntegrationTest;
import com.abdullah.msal2.push.WebPush;

import tools.jackson.databind.JsonNode;

/**
 * SRV-011 (BANK-SMS.md 4.3 to 4.7): the shortcut's endpoint with its personal token, the review
 * (approve or ignore) and the settings. "Now" is 2026-11-11 08:00 UTC, 12:00 in Dubai. Every
 * message is made up (the repository is public).
 */
class BankSmsApiTest extends IntegrationTest {

	private static final String INGEST = "/api/v1/sms/ingest";

	// ── Helpers ─────────────────────────────────────────────────

	/** The pending entry of a merchant (the fixed clock gives them all the same time). */
	private static JsonNode byMerchant(JsonNode list, String merchant) {
		for (JsonNode p : list) {
			if (p.get("merchant").asString().equals(merchant)) {
				return p;
			}
		}
		throw new AssertionError("No pending entry for " + merchant);
	}

	/** A null value, whether written as null or left out. */
	private static boolean absent(JsonNode node) {
		return node == null || node.isNull() || node.isMissingNode();
	}

	private String newToken(TestUser user) throws Exception {
		return json(call(user, post("/api/v1/sms/token")).andExpect(status().isCreated())).get("token").asString();
	}

	private void link(TestUser user, String suffix, String accountId) throws Exception {
		send(user, put("/api/v1/sms/links/" + suffix), Map.of("accountId", accountId)).andExpect(status().isNoContent());
	}

	/** A user with a linked account and a token, ready for the shortcut. */
	private String ready(TestUser user) throws Exception {
		link(user, FakeSms.SUFFIX, accountOf(user));
		return newToken(user);
	}

	private ResultActions ingest(String token, String text) throws Exception {
		return mvc.perform(post(INGEST).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of("text", text))));
	}

	private JsonNode pending(TestUser user) throws Exception {
		return getJson(user, "/api/v1/budgets/" + budgetOf(user) + "/pending-entries");
	}

	private ResultActions keyword(TestUser user, String word, String categoryId) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("keyword", word);
		body.put("categoryId", categoryId);
		return send(user, post("/api/v1/budgets/" + budgetOf(user) + "/merchant-keywords"), body);
	}

	private ResultActions approve(TestUser user, String id, String categoryId, String name, boolean remember)
			throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("categoryId", categoryId);
		body.put("name", name);
		body.put("remember", remember);
		return send(user, post("/api/v1/pending-entries/" + id + "/approve"), body);
	}

	private void addCycle(TestUser user) throws Exception {
		send(user, post("/api/v1/accounts/" + accountOf(user) + "/cycles"),
				Map.of("startDate", "2026-11-01", "allowance", "3000", "expectedNextSalaryDate", "2026-12-01"))
				.andExpect(status().isCreated());
	}

	// ── The shortcut ────────────────────────────────────────────

	@Test
	void aDebitBecomesAPendingEntryWithItsSuggestion() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		keyword(user, "cafe", categoryOf(user, "cat_coffee")).andExpect(status().isCreated());

		ingest(token, debit("12.00", "BLUE FALCON CAFE")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.message").value("طلب جديد: 12.00 د.إ · قهوة وكافيهات"));
		ingest(token, debit("1,250.00", "DESERT FURNITURE")).andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("طلب جديد: 1,250.00 د.إ · اختر الفئة"));

		JsonNode list = pending(user);
		assertThat(list).hasSize(2);
		JsonNode cafe = byMerchant(list, "BLUE FALCON CAFE");
		JsonNode furniture = byMerchant(list, "DESERT FURNITURE");
		assertThat(cafe.get("amount").asString()).isEqualTo("12.00");
		assertThat(cafe.get("merchant").asString()).isEqualTo("BLUE FALCON CAFE");
		assertThat(cafe.get("categoryId").asString()).isEqualTo(categoryOf(user, "cat_coffee"));
		assertThat(cafe.get("accountId").asString()).isEqualTo(accountOf(user));
		assertThat(cafe.get("entryDate").asString()).isEqualTo("2026-11-11");
		assertThat(cafe.get("receivedAt").asString()).isEqualTo("2026-11-11T08:00:00Z");
		// the message exactly as it came, for the arrow on the card (B-03)
		assertThat(cafe.get("smsText").asString()).isEqualTo(debit("12.00", "BLUE FALCON CAFE"));
		assertThat(furniture.get("amount").asString()).isEqualTo("1250.00");
		assertThat(absent(furniture.path("categoryId"))).isTrue();
	}

	@Test
	void aNewPendingEntryPutsTheCountOnTheAppIcon() throws Exception {
		String apple = "https://web.push.apple.com/QGx8device";
		String key = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";
		String auth = "tBHItJI5svbpez7KI4CCXg";
		when(webPush.enabled()).thenReturn(true);
		when(webPush.send(anyString(), anyString(), anyString(), anyString())).thenReturn(WebPush.Outcome.SENT);
		TestUser user = signUp("sara");
		String token = ready(user);
		send(user, post("/api/v1/me/push-subscriptions"),
				Map.of("endpoint", apple, "keys", Map.of("p256dh", key, "auth", auth)))
				.andExpect(status().isNoContent());

		ingest(token, debit("12.00", "BLUE FALCON CAFE")).andExpect(jsonPath("$.status").value("PENDING"));
		ingest(token, debit("7.50", "SAND KIOSK")).andExpect(jsonPath("$.status").value("PENDING"));
		// not new: no notification (B-22)
		ingest(token, debit("7.50", "SAND KIOSK")).andExpect(jsonPath("$.status").value("DUPLICATE"));
		ingest(token, FakeSms.deposit("500.00")).andExpect(jsonPath("$.status").value("IGNORED"));

		ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
		verify(webPush, times(2)).send(eq(apple), eq(key), eq(auth), payloads.capture());
		JsonNode first = jsonMapper.readTree(payloads.getAllValues().get(0)).get("notification");
		JsonNode second = jsonMapper.readTree(payloads.getAllValues().get(1)).get("notification");
		assertThat(first.get("body").asString()).isEqualTo("طلب جديد: 12.00 د.إ · اختر الفئة");
		assertThat(first.get("tag").asString()).isEqualTo("bank-sms");
		assertThat(first.get("data").get("badge").asInt()).isEqualTo(1);
		assertThat(first.get("data").get("onActionClick").get("default").get("url").asString())
				.isEqualTo("expenses/pending");
		assertThat(second.get("data").get("badge").asInt()).isEqualTo(2);
	}

	@Test
	void aFailedNotificationDoesNotChangeTheShortcutsAnswer() throws Exception {
		when(webPush.enabled()).thenReturn(true);
		when(webPush.send(anyString(), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException());
		TestUser user = signUp("sara");
		String token = ready(user);
		send(user, post("/api/v1/me/push-subscriptions"), Map.of("endpoint", "https://web.push.apple.com/QGx8device",
				"keys", Map.of("p256dh", "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM",
						"auth", "tBHItJI5svbpez7KI4CCXg")))
				.andExpect(status().isNoContent());

		ingest(token, debit("12.00", "BLUE FALCON CAFE")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(pending(user)).hasSize(1);
	}

	@Test
	void withoutPushNothingIsSent() throws Exception {
		TestUser user = signUp("sara");
		ingest(ready(user), debit("12.00", "BLUE FALCON CAFE")).andExpect(jsonPath("$.status").value("PENDING"));
		verify(webPush, never()).send(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void theTokenIsCheckedAndUpdatesLastUsed() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		String text = debit("5.00", "SAND KIOSK");

		ingest("msal_wrongwrongwrongwrongwrongwrong12", text).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SYS-E001"));
		mvc.perform(post(INGEST).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
				.andExpect(status().isUnauthorized());
		// The app's JWT is not the shortcut's token
		send(user, post(INGEST), Map.of("text", text)).andExpect(status().isUnauthorized());

		assertThat(absent(getJson(user, "/api/v1/sms/settings").get("token").path("lastUsedAt"))).isTrue();
		ingest(token, text).andExpect(jsonPath("$.status").value("PENDING"));
		JsonNode status = getJson(user, "/api/v1/sms/settings").get("token");
		assertThat(status.get("hint").asString()).isEqualTo(token.substring(token.length() - 4));
		assertThat(status.get("lastUsedAt").asString()).isEqualTo("2026-11-11T08:00:00Z");
	}

	@Test
	void aNewTokenRevokesTheOldOneAndOnlyItsHashIsKept() throws Exception {
		TestUser user = signUp("sara");
		String first = ready(user);
		assertThat(first).startsWith("msal_").hasSize(5 + 32);
		String second = newToken(user);
		assertThat(second).isNotEqualTo(first);

		ingest(first, debit("5.00", "SAND KIOSK")).andExpect(status().isUnauthorized());
		ingest(second, debit("5.00", "SAND KIOSK")).andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(jdbc.queryForObject("select count(*) from msal.sms_tokens where token_hash = ? or token_hash = ?",
				Integer.class, first, second)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from msal.sms_tokens where revoked_at is null", Integer.class))
				.isEqualTo(1);

		call(user, delete("/api/v1/sms/token")).andExpect(status().isNoContent());
		ingest(second, debit("6.00", "SAND KIOSK")).andExpect(status().isUnauthorized());
		assertThat(absent(getJson(user, "/api/v1/sms/settings").path("token"))).isTrue();
	}

	@Test
	void aDisabledUsersTokenStopsWorking() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		jdbc.update("update msal.app_users set status = 'DISABLED' where id = ?", user.id());
		ingest(token, debit("5.00", "SAND KIOSK")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SYS-E009"));
	}

	@Test
	void otherMessagesAreIgnoredWithoutAPendingEntry() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);

		ingest(token, FakeSms.deposit("500.00")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IGNORED")).andExpect(jsonPath("$.message").value("ليست رسالة خصم"));
		ingest(token, debit("5.00", "123", "SAND KIOSK", "Du bai")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IGNORED")).andExpect(jsonPath("$.message").value("حساب غير مربوط"));
		assertThat(pending(user)).isEmpty();
	}

	@Test
	void theSameMessageTwiceIsADuplicate() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		String text = debit("5.00", "SAND KIOSK");

		ingest(token, text).andExpect(jsonPath("$.status").value("PENDING"));
		ingest(token, text).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DUPLICATE"))
				.andExpect(jsonPath("$.message").value("وصلت من قبل"));
		ingest(token, text + "\n").andExpect(jsonPath("$.status").value("DUPLICATE"));
		assertThat(pending(user)).hasSize(1);
	}

	@Test
	void messagesOfAnArchivedAccountAreRefused() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		String budget = budgetOf(user);
		send(user, post("/api/v1/budgets/" + budget + "/accounts"), Map.of("name", "حساب ثاني"))
				.andExpect(status().isCreated());
		JsonNode first = getJson(user, "/api/v1/budgets/" + budget + "/accounts").get(0);
		send(user, patch("/api/v1/accounts/" + first.get("id").asString()),
				Map.of("isArchived", true, "version", first.get("version").asInt()))
				.andExpect(status().isOk());
		ingest(token, debit("5.00", "SAND KIOSK")).andExpect(jsonPath("$.status").value("IGNORED"))
				.andExpect(jsonPath("$.message").value("الحساب المربوط مؤرشف"));
	}

	@Test
	void theDateIsTheDayItArrivedInTheUsersTimeZone() throws Exception {
		// 2026-11-11 08:00 UTC is still 2026-11-10 (21:00) in Pago Pago
		TestUser user = signUp("sara");
		int version = getJson(user, "/api/v1/me").get("version").asInt();
		send(user, patch("/api/v1/me"), Map.of("timeZone", "Pacific/Pago_Pago", "version", version))
				.andExpect(status().isOk());
		String token = ready(user);
		ingest(token, debit("5.00", "SAND KIOSK")).andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(pending(user).get(0).get("entryDate").asString()).isEqualTo("2026-11-10");
	}

	@Test
	void nearMidnightTheDateFollowsDubaiNotTheServer() {
		// 23:59 and 00:01 in Dubai are 19:59 and 20:01 UTC, the same UTC day
		assertThat(SmsIngestService.entryDate(Instant.parse("2026-11-10T19:59:00Z"), "Asia/Dubai"))
				.isEqualTo(LocalDate.parse("2026-11-10"));
		assertThat(SmsIngestService.entryDate(Instant.parse("2026-11-10T20:01:00Z"), "Asia/Dubai"))
				.isEqualTo(LocalDate.parse("2026-11-11"));
	}

	@Test
	void theBodyIsLimitedTo2KB() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		ingest(token, "x".repeat(2100)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS-E006"));
		mvc.perform(post(INGEST).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest());
	}

	@Test
	void theShortcutsEndpointHasNoCors() throws Exception {
		mvc.perform(options(INGEST).header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	// ── Review ──────────────────────────────────────────────────

	@Test
	void approvingAddsTheEntryLikeAnyOtherAndDeletesTheMessage() throws Exception {
		TestUser user = signUp("sara");
		addCycle(user);
		String token = ready(user);
		String home = "/api/v1/accounts/" + accountOf(user) + "/home";
		String stats = "/api/v1/accounts/" + accountOf(user) + "/stats";
		JsonNode homeBefore = getJson(user, home);
		JsonNode statsBefore = getJson(user, stats);

		String text = debit("12.00", "BLUE FALCON CAFETERIA");
		ingest(token, text).andExpect(jsonPath("$.status").value("PENDING"));

		// Waiting for review, it is not an entry: home and the statistics do not change (B-06)
		JsonNode homeWaiting = getJson(user, home);
		assertThat(homeWaiting.get("pendingCount").asInt()).isEqualTo(1);
		assertThat(homeWaiting.get("pendingTotal").asInt()).isEqualTo(1);
		assertThat(homeWaiting.get("selected")).isEqualTo(homeBefore.get("selected"));
		assertThat(homeWaiting.get("recent")).isEqualTo(homeBefore.get("recent"));
		assertThat(getJson(user, stats)).isEqualTo(statsBefore);

		String id = pending(user).get(0).get("id").asString();
		String food = categoryOf(user, "cat_food");
		JsonNode entry = json(approve(user, id, food, "غداء", false).andExpect(status().isCreated()));
		assertThat(entry.get("type").asString()).isEqualTo("EXPENSE");
		assertThat(entry.get("amount").asString()).isEqualTo("12.00");
		assertThat(entry.get("name").asString()).isEqualTo("غداء");
		assertThat(entry.get("categoryId").asString()).isEqualTo(food);
		assertThat(entry.get("entryDate").asString()).isEqualTo("2026-11-11");
		assertThat(entry.get("accountId").asString()).isEqualTo(accountOf(user));
		assertThat(entry.get("cycleKey").asString()).isNotBlank();

		JsonNode homeAfter = getJson(user, home);
		assertThat(homeAfter.get("pendingCount").asInt()).isZero();
		assertThat(homeAfter.get("pendingTotal").asInt()).isZero();
		assertThat(homeAfter.get("selected").get("summary").get("spent").asString()).isEqualTo("12.00");
		assertThat(homeAfter.get("recent").get(0).get("id").asString()).isEqualTo(entry.get("id").asString());

		// The message is gone with the pending entry, and audit_log has no copy of it (B-07)
		assertThat(jdbc.queryForObject("select count(*) from msal.pending_entries", Integer.class)).isZero();
		List<String> audit = jdbc.queryForList("""
				select coalesce(before_data::text, '') || coalesce(after_data::text, '') from msal.audit_log
				where entity_type = 'pending_entry' order by id
				""", String.class);
		assertThat(audit).hasSize(2).allSatisfy(row -> assertThat(row).doesNotContain("Avl Bal").doesNotContain("4321"));
		assertThat(audit.get(1)).contains("BLUE FALCON CAFETERIA").contains(entry.get("id").asString());
	}

	@Test
	void approvingNeedsACategoryAndADescription() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		ingest(token, debit("5.00", "SAND KIOSK"));
		String id = pending(user).get(0).get("id").asString();

		approve(user, id, null, "شاي", false).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-011-E004"));
		approve(user, id, categoryOf(user, "cat_food"), "  ", false).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-001-E003"));
		assertThat(pending(user)).hasSize(1);
		assertThat(getJson(user, "/api/v1/accounts/" + accountOf(user) + "/entries").get("days")).isEmpty();
	}

	@Test
	void anotherDayCanBePickedWhenApproving() throws Exception {
		// Paid after midnight, counted on the day before (B-21)
		TestUser user = signUp("sara");
		String token = ready(user);
		ingest(token, debit("7.00", "SAND KIOSK"));
		String id = pending(user).get(0).get("id").asString();
		String food = categoryOf(user, "cat_food");

		Map<String, Object> bad = new HashMap<>(Map.of("categoryId", food, "name", "شاي", "entryDate", "2026-02-30"));
		send(user, post("/api/v1/pending-entries/" + id + "/approve"), bad).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-001-E004"));
		assertThat(pending(user)).hasSize(1);

		Map<String, Object> body = new HashMap<>(Map.of("categoryId", food, "name", "شاي", "entryDate", "2026-11-10"));
		JsonNode entry = json(send(user, post("/api/v1/pending-entries/" + id + "/approve"), body)
				.andExpect(status().isCreated()));
		assertThat(entry.get("entryDate").asString()).isEqualTo("2026-11-10");
		assertThat(pending(user)).isEmpty();
	}

	@Test
	void ignoringDeletesItWithoutAnEntry() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		ingest(token, debit("5.00", "SAND KIOSK"));
		String id = pending(user).get(0).get("id").asString();

		call(user, delete("/api/v1/pending-entries/" + id)).andExpect(status().isNoContent());
		assertThat(pending(user)).isEmpty();
		assertThat(getJson(user, "/api/v1/accounts/" + accountOf(user) + "/entries").get("days")).isEmpty();
		call(user, delete("/api/v1/pending-entries/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void rememberingAMerchantAddsItsWholeNameAndSuggestsItOnTheOtherCards() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		ingest(token, debit("10.00", FakeSms.SUFFIX, "Al-Sarab cafeteria", "DU BAI"));
		ingest(token, debit("14.00", FakeSms.SUFFIX, "AL SARAB CAFETERIA", "Sh arjah"));
		JsonNode list = pending(user);
		assertThat(absent(list.get(0).path("categoryId"))).isTrue();

		String food = categoryOf(user, "cat_food");
		approve(user, list.get(1).get("id").asString(), food, "ريوق", true).andExpect(status().isCreated());

		JsonNode words = getJson(user, "/api/v1/sms/settings").get("keywords");
		assertThat(words).hasSize(1);
		assertThat(words.get(0).get("keyword").asString()).isEqualTo("AL SARAB CAFETERIA");
		assertThat(words.get(0).get("categoryId").asString()).isEqualTo(food);
		assertThat(pending(user).get(0).get("categoryId").asString()).isEqualTo(food);

		// Remembered again for another category, the word moves (B-16)
		String coffee = categoryOf(user, "cat_coffee");
		approve(user, pending(user).get(0).get("id").asString(), coffee, "قهوة", true).andExpect(status().isCreated());
		words = getJson(user, "/api/v1/sms/settings").get("keywords");
		assertThat(words).hasSize(1);
		assertThat(words.get(0).get("categoryId").asString()).isEqualTo(coffee);
	}

	@Test
	void pickEveryTimeAndHiddenCategoriesGiveNoSuggestion() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		keyword(user, "FASTBITE", null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.categoryId").doesNotExist());
		keyword(user, "DELIVERY", categoryOf(user, "cat_food")).andExpect(status().isCreated());
		String fun = categoryOf(user, "cat_fun");
		keyword(user, "CINEMA", fun).andExpect(status().isCreated());
		int funVersion = 0;
		for (JsonNode c : getJson(user, "/api/v1/budgets/" + budgetOf(user) + "/categories")) {
			if (c.get("id").asString().equals(fun)) {
				funVersion = c.get("version").asInt();
			}
		}
		send(user, patch("/api/v1/categories/" + fun), Map.of("isHidden", true, "version", funVersion))
				.andExpect(status().isOk());

		ingest(token, debit("30.00", "FASTBITE DELIVERY")).andExpect(jsonPath("$.message").value(
				"طلب جديد: 30.00 د.إ · اختر الفئة"));
		ingest(token, debit("45.00", "STAR CINEMA")).andExpect(jsonPath("$.message").value(
				"طلب جديد: 45.00 د.إ · اختر الفئة"));
		JsonNode list = pending(user);
		JsonNode cinema = byMerchant(list, "STAR CINEMA");
		JsonNode fastbite = byMerchant(list, "FASTBITE DELIVERY");
		assertThat(fastbite.get("askEachTime").asBoolean()).isTrue();
		assertThat(absent(fastbite.path("categoryId"))).isTrue();
		assertThat(cinema.get("askEachTime").asBoolean()).isFalse();
		assertThat(absent(cinema.path("categoryId"))).isTrue();
	}

	// ── Settings ────────────────────────────────────────────────

	@Test
	void linksAreCheckedListedAndRemoved() throws Exception {
		TestUser user = signUp("sara");
		String account = accountOf(user);
		send(user, put("/api/v1/sms/links/12"), Map.of("accountId", account)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-011-E001"));
		send(user, put("/api/v1/sms/links/12a4"), Map.of("accountId", account))
				.andExpect(jsonPath("$.code").value("SRV-011-E001"));
		send(user, put("/api/v1/sms/links/1234567"), Map.of("accountId", account))
				.andExpect(jsonPath("$.code").value("SRV-011-E001"));
		link(user, "987", account);
		link(user, "987", account);

		JsonNode links = getJson(user, "/api/v1/sms/settings").get("links");
		assertThat(links).hasSize(1);
		assertThat(links.get(0).get("suffix").asString()).isEqualTo("987");
		assertThat(links.get(0).get("accountId").asString()).isEqualTo(account);
		assertThat(links.get(0).get("accountName").asString()).isEqualTo("مصاريفي");
		assertThat(links.get(0).get("budgetId").asString()).isEqualTo(budgetOf(user));

		call(user, delete("/api/v1/sms/links/987")).andExpect(status().isNoContent());
		call(user, delete("/api/v1/sms/links/987")).andExpect(status().isNotFound());
		assertThat(getJson(user, "/api/v1/sms/settings").get("links")).isEmpty();
	}

	@Test
	void keywordsAreCleanedCheckedAndDeleted() throws Exception {
		TestUser user = signUp("sara");
		String coffee = categoryOf(user, "cat_coffee");
		JsonNode saved = json(keyword(user, " blue-falcon. ", coffee).andExpect(status().isCreated()));
		assertThat(saved.get("keyword").asString()).isEqualTo("BLUE FALCON");

		keyword(user, " - ", coffee).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SRV-011-E002"));
		keyword(user, null, coffee).andExpect(jsonPath("$.code").value("SRV-011-E002"));
		keyword(user, "A".repeat(41), coffee).andExpect(jsonPath("$.code").value("SRV-011-E003"));
		keyword(user, "A".repeat(40), coffee).andExpect(status().isCreated());

		call(user, delete("/api/v1/merchant-keywords/" + saved.get("id").asString())).andExpect(status().isNoContent());
		assertThat(getJson(user, "/api/v1/sms/settings").get("keywords")).hasSize(1);
	}

	@Test
	void withoutTheServiceNothingWorks() throws Exception {
		TestUser user = signUp("sara");
		String token = ready(user);
		jdbc.update("update msal.services set enabled = false where code = 'SRV-011'");
		ingest(token, debit("5.00", "SAND KIOSK")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SYS-E003"));
		call(user, get("/api/v1/sms/settings")).andExpect(jsonPath("$.code").value("SYS-E003"));
	}

	// ── Isolation (rule 11) ─────────────────────────────────────

	@Test
	void noUserSeesOrChangesAnotherUsersMessagesLinksOrWords() throws Exception {
		TestUser bob = signUp("bob");
		String bobToken = ready(bob);
		ingest(bobToken, debit("77.77", "BOBS SECRET SHOP")).andExpect(jsonPath("$.status").value("PENDING"));
		String bobPending = pending(bob).get(0).get("id").asString();
		String bobBudget = budgetOf(bob);
		String bobAccount = accountOf(bob);
		String bobCategory = categoryOf(bob, "cat_food");
		String bobWord = json(keyword(bob, "BOBWORD", bobCategory)).get("id").asString();

		TestUser alice = signUp("alice");
		String aliceToken = ready(alice);

		// Bob's records are not found, never forbidden
		for (ResultActions r : List.of(
				call(alice, get("/api/v1/budgets/" + bobBudget + "/pending-entries")),
				approve(alice, bobPending, categoryOf(alice, "cat_food"), "x", false),
				approve(alice, bobPending, bobCategory, "x", false),
				call(alice, delete("/api/v1/pending-entries/" + bobPending)),
				send(alice, put("/api/v1/sms/links/555"), Map.of("accountId", bobAccount)),
				send(alice, post("/api/v1/budgets/" + bobBudget + "/merchant-keywords"),
						Map.of("keyword", "X", "categoryId", categoryOf(alice, "cat_food"))),
				send(alice, post("/api/v1/budgets/" + budgetOf(alice) + "/merchant-keywords"),
						Map.of("keyword", "X", "categoryId", bobCategory)),
				call(alice, delete("/api/v1/merchant-keywords/" + bobWord)))) {
			r.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SYS-E004"));
			assertThat(r.andReturn().getResponse().getContentAsString()).doesNotContain("77.77").doesNotContain("BOBS");
		}

		// Alice's own pending entry cannot be approved into Bob's category
		ingest(aliceToken, debit("3.00", "SAND KIOSK")).andExpect(jsonPath("$.status").value("PENDING"));
		approve(alice, pending(alice).get(0).get("id").asString(), bobCategory, "x", false)
				.andExpect(status().isNotFound());

		// Bob's digits are linked for Bob only; Alice's settings show nothing of his
		ingest(aliceToken, debit("4.00", "999", "SAND KIOSK", "Du bai")).andExpect(jsonPath("$.status").value("IGNORED"));
		JsonNode settings = getJson(alice, "/api/v1/sms/settings");
		assertThat(settings.get("links")).hasSize(1);
		assertThat(settings.get("links").get(0).get("accountId").asString()).isEqualTo(accountOf(alice));
		assertThat(settings.get("keywords")).isEmpty();
		assertThat(settings.toString()).doesNotContain(bobAccount);

		// Bob's data is unchanged
		assertThat(pending(bob)).hasSize(1);
		assertThat(getJson(bob, "/api/v1/sms/settings").get("keywords")).hasSize(1);
		assertThat(getJson(bob, "/api/v1/sms/settings").get("links").get(0).get("accountId").asString())
				.isEqualTo(bobAccount);
	}
}
