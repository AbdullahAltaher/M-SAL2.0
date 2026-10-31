package com.abdullah.msal2.expenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.IntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * SRV-001, SRV-006 and SRV-008 through the API. "Today" is 2026-11-11 in Dubai.
 */
class EntryApiTest extends IntegrationTest {

	private Map<String, Object> entry(TestUser user, String amount, String name, String date) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("amount", amount);
		body.put("name", name);
		body.put("categoryId", categoryOf(user, "cat_food"));
		body.put("entryDate", date);
		body.put("accountId", accountOf(user));
		return body;
	}

	private JsonNode add(TestUser user, Map<String, Object> body) throws Exception {
		return json(send(user, post("/api/v1/budgets/" + budgetOf(user) + "/entries"), body)
				.andExpect(status().isCreated()));
	}

	private void businessRulesCycle(TestUser user) throws Exception {
		send(user, post("/api/v1/accounts/" + accountOf(user) + "/cycles"),
				Map.of("startDate", "2026-11-01", "allowance", "3000", "expectedNextSalaryDate", "2026-12-01"))
				.andExpect(status().isCreated());
	}

	@Test
	void validationFollowsTheOrderOfTheBusinessRules() throws Exception {
		TestUser user = signUp("sara");
		String path = "/api/v1/budgets/" + budgetOf(user) + "/entries";
		Map<String, Object> body = entry(user, "0", " ", "2026-02-30");
		body.put("categoryId", null);
		body.put("accountId", null);
		send(user, post(path), body).andExpect(jsonPath("$.code").value("SRV-001-E001"));
		body.put("amount", "46.25");
		send(user, post(path), body).andExpect(jsonPath("$.code").value("SRV-001-E002"));
		body.put("categoryId", categoryOf(user, "cat_food"));
		send(user, post(path), body).andExpect(jsonPath("$.code").value("SRV-001-E003"));
		body.put("name", " غدا ");
		send(user, post(path), body).andExpect(jsonPath("$.code").value("SRV-001-E004"));
		body.put("entryDate", "2026-11-11");
		send(user, post(path), body).andExpect(jsonPath("$.code").value("SRV-001-E005"));
		body.put("accountId", accountOf(user));
		send(user, post(path), body).andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("غدا"))
				.andExpect(jsonPath("$.amount").value("46.25"))
				.andExpect(jsonPath("$.type").value("EXPENSE"))
				.andExpect(jsonPath("$.cycleKey").value("none"));

		for (String bad : new String[] { "-5", "1.234", "1e3", "1000000000", "٤٦" }) {
			send(user, post(path), entry(user, bad, "x", "2026-11-11")).andExpect(jsonPath("$.code").value("SRV-001-E001"));
		}
	}

	@Test
	void homeShowsTheBusinessRulesExample() throws Exception {
		TestUser user = signUp("sara");
		businessRulesCycle(user);
		add(user, entry(user, "1000", "إيجار", "2026-11-05"));
		JsonNode today = add(user, entry(user, "30", "قهوة", "2026-11-11"));
		assertThat(today.get("cycleKey").asString()).isNotEqualTo("none");

		call(user, get("/api/v1/accounts/" + accountOf(user) + "/home")).andExpect(status().isOk())
				.andExpect(jsonPath("$.today").value("2026-11-11"))
				.andExpect(jsonPath("$.account.name").value("مصاريفي"))
				.andExpect(jsonPath("$.cycleKeys.length()").value(1))
				.andExpect(jsonPath("$.selected.cycle.isOpen").value(true))
				.andExpect(jsonPath("$.selected.summary.startOfToday").value("2000.00"))
				.andExpect(jsonPath("$.selected.summary.daysLeft").value(20))
				.andExpect(jsonPath("$.selected.summary.todayBudget").value("100.00"))
				.andExpect(jsonPath("$.selected.summary.canSpendToday").value("70.00"))
				.andExpect(jsonPath("$.selected.summary.spent").value("1030.00"))
				.andExpect(jsonPath("$.selected.summary.progress").value(0.3433))
				.andExpect(jsonPath("$.selected.summary.tomorrow").doesNotExist())
				.andExpect(jsonPath("$.recent[0].name").value("قهوة"))
				.andExpect(jsonPath("$.recent.length()").value(2));

		add(user, entry(user, "120", "عشاء", "2026-11-11"));
		call(user, get("/api/v1/accounts/" + accountOf(user) + "/home"))
				.andExpect(jsonPath("$.selected.summary.canSpendToday").value("-50.00"))
				.andExpect(jsonPath("$.selected.summary.overToday").value("50.00"))
				.andExpect(jsonPath("$.selected.summary.tomorrow").value("97.37"));
	}

	@Test
	void listGroupsByDayAndSearchesWithArabicNormalization() throws Exception {
		TestUser user = signUp("sara");
		add(user, entry(user, "10", "قَهْوَة", "2026-11-10"));
		add(user, entry(user, "5", "غداء", "2026-11-11"));
		Map<String, Object> income = entry(user, "100", "مكافأة", "2026-11-11");
		income.put("type", "INCOME");
		add(user, income);
		String path = "/api/v1/accounts/" + accountOf(user) + "/entries";

		call(user, get(path)).andExpect(jsonPath("$.days.length()").value(2))
				.andExpect(jsonPath("$.days[0].date").value("2026-11-11"))
				.andExpect(jsonPath("$.days[0].spent").value("5.00"))
				.andExpect(jsonPath("$.days[0].extra").value("100.00"));
		call(user, get(path + "?q=قهوه")).andExpect(jsonPath("$.days.length()").value(1))
				.andExpect(jsonPath("$.days[0].entries[0].name").value("قَهْوَة"));
		// The category name "أكل ومطاعم" matches too
		call(user, get(path + "?q=مطاعم")).andExpect(jsonPath("$.days.length()").value(2));
	}

	@Test
	void deleteRestorePurgeAndTheTrash() throws Exception {
		TestUser user = signUp("sara");
		String id = add(user, entry(user, "10", "غدا", "2026-11-11")).get("id").asString();
		call(user, post("/api/v1/entries/" + id + "/restore")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SRV-001-E006"));

		call(user, delete("/api/v1/entries/" + id)).andExpect(status().isNoContent());
		call(user, get("/api/v1/accounts/" + accountOf(user) + "/entries")).andExpect(jsonPath("$.days.length()").value(0));
		String trash = "/api/v1/budgets/" + budgetOf(user) + "/entries/deleted";
		call(user, get(trash)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].deletedAt").exists());
		call(user, post("/api/v1/entries/" + id + "/restore")).andExpect(status().isOk())
				.andExpect(jsonPath("$.deletedAt").doesNotExist());

		call(user, delete("/api/v1/entries/" + id));
		call(user, delete(trash)).andExpect(status().isOk()).andExpect(jsonPath("$.purged").value(1));
		call(user, get("/api/v1/entries/" + id)).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select count(*) from msal.audit_log where action = 'PURGE'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void duplicateIsDatedTodayAndPermanentDeleteUndoesIt() throws Exception {
		TestUser user = signUp("sara");
		String id = add(user, entry(user, "46.25", "غدا", "2026-11-01")).get("id").asString();
		JsonNode copy = json(call(user, post("/api/v1/entries/" + id + "/duplicate")).andExpect(status().isCreated()));
		assertThat(copy.get("entryDate").asString()).isEqualTo("2026-11-11");
		assertThat(copy.get("id").asString()).isNotEqualTo(id);
		call(user, delete("/api/v1/entries/" + copy.get("id").asString() + "/permanent")).andExpect(status().isNoContent());
		call(user, get("/api/v1/entries/" + copy.get("id").asString())).andExpect(status().isNotFound());
	}

	@Test
	void editingChecksTheVersionAndTheFields() throws Exception {
		TestUser user = signUp("sara");
		String id = add(user, entry(user, "10", "غدا", "2026-11-11")).get("id").asString();
		send(user, patch("/api/v1/entries/" + id), Map.of("amount", "12.50", "version", 0)).andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value("12.50")).andExpect(jsonPath("$.version").value(1));
		send(user, patch("/api/v1/entries/" + id), Map.of("name", "عشا", "version", 0)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SYS-E005"));
		send(user, patch("/api/v1/entries/" + id), Map.of("name", "  ", "version", 1)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-001-E003"));
	}

	@Test
	void lastExpenseAndSuggestions() throws Exception {
		TestUser user = signUp("sara");
		String account = accountOf(user);
		call(user, get("/api/v1/accounts/" + account + "/entries/last-expense")).andExpect(status().isNoContent());
		String suggestions = "/api/v1/budgets/" + budgetOf(user) + "/entries/suggestions?categoryId="
				+ categoryOf(user, "cat_food");
		call(user, get(suggestions)).andExpect(jsonPath("$.suggestions[0]").value("ريوق"))
				.andExpect(jsonPath("$.suggestions.length()").value(3));

		add(user, entry(user, "10", "شاورما", "2026-11-10"));
		call(user, get(suggestions)).andExpect(jsonPath("$.suggestions.length()").value(1))
				.andExpect(jsonPath("$.suggestions[0]").value("شاورما"));
		call(user, get("/api/v1/accounts/" + account + "/entries/last-expense")).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("شاورما"));
	}

	@Test
	void statsAndReceiptOfTheCycle() throws Exception {
		TestUser user = signUp("sara");
		businessRulesCycle(user);
		add(user, entry(user, "1000", "إيجار", "2026-11-05"));
		Map<String, Object> coffee = entry(user, "30", "قهوة", "2026-11-11");
		coffee.put("categoryId", categoryOf(user, "cat_coffee"));
		add(user, coffee);
		String account = accountOf(user);

		call(user, get("/api/v1/accounts/" + account + "/stats")).andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value("1030.00"))
				.andExpect(jsonPath("$.count").value(2))
				.andExpect(jsonPath("$.dailyAverage").value("93.64"))
				.andExpect(jsonPath("$.categories[0].pct").value(97))
				.andExpect(jsonPath("$.categories[1].pct").value(3))
				.andExpect(jsonPath("$.lastDays.length()").value(7))
				.andExpect(jsonPath("$.trend.length()").value(1))
				.andExpect(jsonPath("$.hasPreviousCycle").value(false));

		call(user, get("/api/v1/accounts/" + account + "/receipt")).andExpect(status().isOk())
				.andExpect(jsonPath("$.accountName").value("مصاريفي"))
				.andExpect(jsonPath("$.start").value("2026-11-01"))
				.andExpect(jsonPath("$.end").value("2026-11-11"))
				.andExpect(jsonPath("$.remaining").value("1970.00"))
				.andExpect(jsonPath("$.categories[0].name").value("أكل ومطاعم"));
	}
}
