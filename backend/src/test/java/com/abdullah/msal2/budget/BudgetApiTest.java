package com.abdullah.msal2.budget;

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
 * SRV-002 through the API: R-BUD-01 to R-BUD-06.
 */
class BudgetApiTest extends IntegrationTest {

	private static Map<String, Object> body(Object... pairs) {
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	@Test
	void firstLoginCreatesTheDefaultBudget() throws Exception {
		TestUser user = signUp("sara");
		String budgetId = budgetOf(user);
		JsonNode budget = getJson(user, "/api/v1/budgets/" + budgetId);
		assertThat(budget.get("name").asString()).isEqualTo("الأساسي");
		assertThat(budget.get("monthLabel").asString()).isEqualTo("نوفمبر 2026");
		assertThat(budget.get("salaryLabel").asString()).isEqualTo("الراتب");
		assertThat(budget.get("groups")).hasSize(1);
		assertThat(budget.get("groups").get(0).get("title").asString()).isEqualTo("التزامات ثابتة");
		assertThat(budget.get("groups").get(0).get("items")).hasSize(1);

		JsonNode accounts = getJson(user, "/api/v1/budgets/" + budgetId + "/accounts");
		assertThat(accounts).hasSize(1);
		assertThat(accounts.get(0).get("name").asString()).isEqualTo("مصاريفي");
		assertThat(budget.get("lastAccountId").asString()).isEqualTo(accounts.get(0).get("id").asString());
		assertThat(getJson(user, "/api/v1/budgets/" + budgetId + "/categories")).hasSize(20);
	}

	@Test
	void totalsFollowTheBusinessRulesExample() throws Exception {
		TestUser user = signUp("sara");
		String budgetId = budgetOf(user);
		JsonNode budget = getJson(user, "/api/v1/budgets/" + budgetId);
		send(user, patch("/api/v1/budgets/" + budgetId),
				body("salary", body("amount", "15000", "isApprox", false), "version", budget.get("version").asInt()))
				.andExpect(status().isOk());

		JsonNode group = budget.get("groups").get(0);
		JsonNode rent = group.get("items").get(0);
		send(user, patch("/api/v1/budget-items/" + rent.get("id").asString()),
				body("name", "إيجار", "amount", "5000", "isDone", true, "version", 0)).andExpect(status().isOk());
		JsonNode electricity = json(call(user, post("/api/v1/budget-groups/" + group.get("id").asString() + "/items"))
				.andExpect(status().isCreated()));
		send(user, patch("/api/v1/budget-items/" + electricity.get("id").asString()),
				body("name", "كهرباء", "amount", "400", "isApprox", true, "version", 0)).andExpect(status().isOk());

		JsonNode loans = json(send(user, post("/api/v1/budgets/" + budgetId + "/groups"), body("title", "أقساط"))
				.andExpect(status().isCreated()));
		send(user, patch("/api/v1/budget-items/" + loans.get("items").get(0).get("id").asString()),
				body("name", "سيارة", "amount", "2500", "version", 0)).andExpect(status().isOk());

		call(user, get("/api/v1/budgets/" + budgetId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.salary.amount").value("15000.00"))
				.andExpect(jsonPath("$.groups[0].totals.subtotal").value("5400.00"))
				.andExpect(jsonPath("$.groups[0].totals.subtotalApprox").value(true))
				.andExpect(jsonPath("$.groups[0].totals.balanceAfter").value("9600.00"))
				.andExpect(jsonPath("$.groups[1].totals.subtotal").value("2500.00"))
				.andExpect(jsonPath("$.groups[1].totals.subtotalApprox").value(false))
				.andExpect(jsonPath("$.groups[1].totals.balanceAfter").value("7100.00"))
				.andExpect(jsonPath("$.groups[1].totals.balanceApprox").value(true))
				.andExpect(jsonPath("$.totals.final").value("7100.00"))
				.andExpect(jsonPath("$.totals.finalApprox").value(true));
	}

	@Test
	void anItemAmountCanBeClearedWithNullAndABadAmountIsRefused() throws Exception {
		TestUser user = signUp("sara");
		JsonNode item = getJson(user, "/api/v1/budgets/" + budgetOf(user)).get("groups").get(0).get("items").get(0);
		String path = "/api/v1/budget-items/" + item.get("id").asString();
		send(user, patch(path), body("amount", "-50.5", "version", 0)).andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value("-50.50"));
		send(user, patch(path), body("amount", null, "version", 1)).andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").doesNotExist())
				.andExpect(jsonPath("$.version").value(2));
		send(user, patch(path), body("amount", "12abc", "version", 2)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-002-E003"));
		send(user, patch(path), body("name", "x", "version", 0)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SYS-E005"));
	}

	@Test
	void newMonthClearsDoneMarksKeepsAmountsAndIsAudited() throws Exception {
		TestUser user = signUp("sara");
		String budgetId = budgetOf(user);
		JsonNode item = getJson(user, "/api/v1/budgets/" + budgetId).get("groups").get(0).get("items").get(0);
		send(user, patch("/api/v1/budget-items/" + item.get("id").asString()),
				body("amount", "300", "isDone", true, "version", 0)).andExpect(status().isOk());
		send(user, patch("/api/v1/budgets/" + budgetId), body("monthLabel", "أكتوبر 2026", "version",
				getJson(user, "/api/v1/budgets/" + budgetId).get("version").asInt())).andExpect(status().isOk());

		call(user, post("/api/v1/budgets/" + budgetId + "/new-month")).andExpect(status().isOk())
				.andExpect(jsonPath("$.monthLabel").value("نوفمبر 2026"))
				.andExpect(jsonPath("$.groups[0].items[0].isDone").value(false))
				.andExpect(jsonPath("$.groups[0].items[0].amount").value("300.00"));
		assertThat(jdbc.queryForObject(
				"select before_data::text from msal.audit_log where action = 'NEW_MONTH' and entity_id = ?::uuid",
				String.class, budgetId)).contains("أكتوبر 2026").contains(item.get("id").asString());
	}

	@Test
	void createNeedsANameAndBecomesTheOpenBudget() throws Exception {
		TestUser user = signUp("sara");
		send(user, post("/api/v1/budgets"), body("name", "   ")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-002-E001"));
		JsonNode created = json(send(user, post("/api/v1/budgets"), body("name", " أمي "))
				.andExpect(status().isCreated()));
		assertThat(created.get("name").asString()).isEqualTo("أمي");
		assertThat(budgetOf(user)).isEqualTo(created.get("id").asString());
		assertThat(getJson(user, "/api/v1/budgets")).hasSize(2);
	}

	@Test
	void theLastBudgetCannotBeDeletedAndDeletingMovesTheOpenBudget() throws Exception {
		TestUser user = signUp("sara");
		String first = budgetOf(user);
		call(user, delete("/api/v1/budgets/" + first)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SRV-002-E002"));

		String second = json(send(user, post("/api/v1/budgets"), body("name", "ثاني"))).get("id").asString();
		call(user, delete("/api/v1/budgets/" + second)).andExpect(status().isNoContent());
		assertThat(budgetOf(user)).isEqualTo(first);
		call(user, get("/api/v1/budgets/" + second)).andExpect(status().isNotFound());
		assertThat(getJson(user, "/api/v1/budgets")).hasSize(1);
	}

	@Test
	void deletingAGroupOrItemIsHardAndKeptInTheAuditLog() throws Exception {
		TestUser user = signUp("sara");
		String budgetId = budgetOf(user);
		JsonNode group = json(send(user, post("/api/v1/budgets/" + budgetId + "/groups"), body("title", "مؤقت")));
		String itemId = group.get("items").get(0).get("id").asString();
		send(user, patch("/api/v1/budget-items/" + itemId), body("name", "شيء", "amount", "10", "version", 0));

		call(user, delete("/api/v1/budget-groups/" + group.get("id").asString())).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from msal.budget_items where id = ?::uuid", Integer.class, itemId))
				.isZero();
		assertThat(jdbc.queryForObject(
				"select before_data::text from msal.audit_log where action = 'DELETE' and entity_type = 'budget_group'",
				String.class)).contains("مؤقت").contains("شيء");

		String keep = getJson(user, "/api/v1/budgets/" + budgetId).get("groups").get(0).get("items").get(0).get("id")
				.asString();
		call(user, delete("/api/v1/budget-items/" + keep)).andExpect(status().isNoContent());
		assertThat(getJson(user, "/api/v1/budgets/" + budgetId).get("groups").get(0).get("items")).isEmpty();
	}
}
