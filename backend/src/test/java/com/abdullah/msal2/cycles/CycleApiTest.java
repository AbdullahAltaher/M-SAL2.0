package com.abdullah.msal2.cycles;

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
 * SRV-003 through the API: R-CYC-01 to R-CYC-03, R-CYC-07 and R-CYC-08.
 */
class CycleApiTest extends IntegrationTest {

	private static Map<String, Object> cycle(String start, String allowance, String expected) {
		Map<String, Object> body = new HashMap<>();
		body.put("startDate", start);
		body.put("allowance", allowance);
		body.put("expectedNextSalaryDate", expected);
		return body;
	}

	private void addExpense(TestUser user, String date, String amount) throws Exception {
		send(user, post("/api/v1/budgets/" + budgetOf(user) + "/entries"),
				Map.of("amount", amount, "name", "x", "categoryId", categoryOf(user, "cat_food"), "entryDate", date,
						"accountId", accountOf(user)))
				.andExpect(status().isCreated());
	}

	@Test
	void validationErrorsHaveTheirOwnCodes() throws Exception {
		TestUser user = signUp("sara");
		String path = "/api/v1/accounts/" + accountOf(user) + "/cycles";
		send(user, post(path), cycle("2026-02-30", "3000", null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-003-E001"));
		send(user, post(path), cycle("2026-11-01", "", null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-003-E002"));
		send(user, post(path), cycle("2026-11-01", "-5", null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-003-E002"));
		send(user, post(path), cycle("2026-11-01", "3000", "2026-11-01")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-003-E003"));
		send(user, post(path), cycle("2026-11-01", "0", null)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.allowance").value("0.00"));
		send(user, post(path), cycle("2026-11-01", "100", null)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SRV-003-E004"));
	}

	@Test
	void cyclesListTheirBoundsAndTheNoCycleFlag() throws Exception {
		TestUser user = signUp("sara");
		String account = accountOf(user);
		send(user, post("/api/v1/accounts/" + account + "/cycles"), cycle("2026-10-01", "3000", null));
		send(user, post("/api/v1/accounts/" + account + "/cycles"), cycle("2026-11-01", "3200", "2026-12-01"));
		call(user, get("/api/v1/accounts/" + account + "/cycles")).andExpect(status().isOk())
				.andExpect(jsonPath("$.cycles[0].startDate").value("2026-10-01"))
				.andExpect(jsonPath("$.cycles[0].lastDay").value("2026-10-31"))
				.andExpect(jsonPath("$.cycles[0].isOpen").value(false))
				.andExpect(jsonPath("$.cycles[1].isOpen").value(true))
				.andExpect(jsonPath("$.cycles[1].lastDay").doesNotExist())
				.andExpect(jsonPath("$.hasNoCycleEntries").value(false));

		addExpense(user, "2026-09-20", "5");
		call(user, get("/api/v1/accounts/" + account + "/cycles")).andExpect(jsonPath("$.hasNoCycleEntries").value(true));
		// Prefill: today in Dubai and the latest allowance (R-CYC-02)
		call(user, get("/api/v1/accounts/" + account + "/cycles/prefill"))
				.andExpect(jsonPath("$.startDate").value("2026-11-11"))
				.andExpect(jsonPath("$.allowance").value("3200.00"));
	}

	@Test
	void movingADateShowsTheImpactAndClearingTheNextSalaryDateWorks() throws Exception {
		TestUser user = signUp("sara");
		String account = accountOf(user);
		send(user, post("/api/v1/accounts/" + account + "/cycles"), cycle("2026-10-01", "3000", null));
		JsonNode b = json(send(user, post("/api/v1/accounts/" + account + "/cycles"),
				cycle("2026-11-01", "3000", "2026-12-01")));
		addExpense(user, "2026-10-28", "1");
		addExpense(user, "2026-10-30", "1");
		addExpense(user, "2026-11-02", "1");
		String bPath = "/api/v1/salary-cycles/" + b.get("id").asString();

		call(user, get(bPath + "/move-impact?startDate=2026-10-28")).andExpect(jsonPath("$.movedEntries").value(2));
		send(user, patch(bPath), Map.of("startDate", "2026-10-01", "version", 0)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SRV-003-E004"));

		Map<String, Object> clear = new HashMap<>();
		clear.put("expectedNextSalaryDate", null);
		clear.put("version", 0);
		send(user, patch(bPath), clear).andExpect(status().isOk())
				.andExpect(jsonPath("$.expectedNextSalaryDate").doesNotExist())
				.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void deletingACycleKeepsItsEntries() throws Exception {
		TestUser user = signUp("sara");
		String account = accountOf(user);
		JsonNode a = json(send(user, post("/api/v1/accounts/" + account + "/cycles"), cycle("2026-10-01", "3000", null)));
		addExpense(user, "2026-10-05", "12");
		call(user, delete("/api/v1/salary-cycles/" + a.get("id").asString())).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from msal.entries", Integer.class)).isEqualTo(1);
		call(user, get("/api/v1/accounts/" + account + "/home")).andExpect(jsonPath("$.selected.key").value("none"))
				.andExpect(jsonPath("$.selected.noCycleTotal").value("12.00"));
		assertThat(jdbc.queryForObject(
				"select before_data::text from msal.audit_log where entity_type = 'salary_cycle' and action = 'DELETE'",
				String.class)).contains("2026-10-01");
	}
}
