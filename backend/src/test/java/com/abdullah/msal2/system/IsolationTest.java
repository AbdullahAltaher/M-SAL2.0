package com.abdullah.msal2.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.abdullah.msal2.IntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * Data isolation (CLAUDE.md rule 11): a user can neither see nor change another user's data.
 * Every endpoint that takes an id is called by Alice with Bob's ids, and must answer 404
 * ({@code SYS-E004}), never 403, so not even the existence of Bob's records leaks.
 */
class IsolationTest extends IntegrationTest {

	private record Call(String label, MockHttpServletRequestBuilder request, Object body) {
	}

	/** Bob's ids. */
	private String budget;
	private String group;
	private String item;
	private String account;
	private String category;
	private String cycle;
	private String entry;
	private String deletedEntry;

	private TestUser bob() throws Exception {
		TestUser bob = signUp("bob");
		budget = budgetOf(bob);
		JsonNode b = getJson(bob, "/api/v1/budgets/" + budget);
		group = b.get("groups").get(0).get("id").asString();
		item = b.get("groups").get(0).get("items").get(0).get("id").asString();
		account = accountOf(bob);
		category = categoryOf(bob, "cat_food");
		cycle = json(send(bob, post("/api/v1/accounts/" + account + "/cycles"),
				Map.of("startDate", "2026-11-01", "allowance", "3000", "expectedNextSalaryDate", "2026-12-01")))
				.get("id").asString();
		entry = addEntry(bob, "عشاء بوب");
		deletedEntry = addEntry(bob, "محذوف");
		mvc.perform(delete("/api/v1/entries/" + deletedEntry).with(bob.token())).andExpect(status().isNoContent());
		return bob;
	}

	private String addEntry(TestUser user, String name) throws Exception {
		return json(send(user, post("/api/v1/budgets/" + budgetOf(user) + "/entries"),
				Map.of("amount", "77.77", "name", name, "categoryId", categoryOf(user, "cat_food"), "entryDate",
						"2026-11-05", "accountId", accountOf(user)))
				.andExpect(status().isCreated())).get("id").asString();
	}

	private List<Call> callsOnBobsData() {
		List<Call> calls = new ArrayList<>();
		calls.add(new Call("get budget", get("/api/v1/budgets/" + budget), null));
		calls.add(new Call("patch budget", patch("/api/v1/budgets/" + budget), Map.of("name", "x", "version", 0)));
		calls.add(new Call("delete budget", delete("/api/v1/budgets/" + budget), null));
		calls.add(new Call("new month", post("/api/v1/budgets/" + budget + "/new-month"), null));
		calls.add(new Call("add group", post("/api/v1/budgets/" + budget + "/groups"), Map.of("title", "x")));
		calls.add(new Call("patch group", patch("/api/v1/budget-groups/" + group), Map.of("title", "x", "version", 0)));
		calls.add(new Call("delete group", delete("/api/v1/budget-groups/" + group), null));
		calls.add(new Call("add item", post("/api/v1/budget-groups/" + group + "/items"), null));
		calls.add(new Call("patch item", patch("/api/v1/budget-items/" + item), Map.of("name", "x", "version", 0)));
		calls.add(new Call("delete item", delete("/api/v1/budget-items/" + item), null));
		calls.add(new Call("list accounts", get("/api/v1/budgets/" + budget + "/accounts"), null));
		calls.add(new Call("add account", post("/api/v1/budgets/" + budget + "/accounts"), Map.of("name", "x")));
		calls.add(new Call("patch account", patch("/api/v1/accounts/" + account), Map.of("name", "x", "version", 0)));
		calls.add(new Call("list categories", get("/api/v1/budgets/" + budget + "/categories"), null));
		calls.add(new Call("add category", post("/api/v1/budgets/" + budget + "/categories"),
				Map.of("name", "x", "emoji", "🐈")));
		calls.add(new Call("patch category", patch("/api/v1/categories/" + category), Map.of("name", "x", "version", 0)));
		calls.add(new Call("reorder categories", put("/api/v1/budgets/" + budget + "/categories/order"),
				Map.of("categoryIds", List.of(category))));
		calls.add(new Call("list cycles", get("/api/v1/accounts/" + account + "/cycles"), null));
		calls.add(new Call("prefill", get("/api/v1/accounts/" + account + "/cycles/prefill"), null));
		calls.add(new Call("add cycle", post("/api/v1/accounts/" + account + "/cycles"),
				Map.of("startDate", "2026-12-01", "allowance", "1")));
		calls.add(new Call("patch cycle", patch("/api/v1/salary-cycles/" + cycle), Map.of("allowance", "1", "version", 0)));
		calls.add(new Call("move impact", get("/api/v1/salary-cycles/" + cycle + "/move-impact?startDate=2026-10-01"),
				null));
		calls.add(new Call("delete cycle", delete("/api/v1/salary-cycles/" + cycle), null));
		calls.add(new Call("home", get("/api/v1/accounts/" + account + "/home"), null));
		calls.add(new Call("list entries", get("/api/v1/accounts/" + account + "/entries"), null));
		calls.add(new Call("last expense", get("/api/v1/accounts/" + account + "/entries/last-expense"), null));
		calls.add(new Call("add entry", post("/api/v1/budgets/" + budget + "/entries"), Map.of("amount", "1", "name", "x",
				"categoryId", category, "entryDate", "2026-11-11", "accountId", account)));
		calls.add(new Call("get entry", get("/api/v1/entries/" + entry), null));
		calls.add(new Call("patch entry", patch("/api/v1/entries/" + entry), Map.of("name", "x", "version", 0)));
		calls.add(new Call("delete entry", delete("/api/v1/entries/" + entry), null));
		calls.add(new Call("restore entry", post("/api/v1/entries/" + deletedEntry + "/restore"), null));
		calls.add(new Call("duplicate entry", post("/api/v1/entries/" + entry + "/duplicate"), null));
		calls.add(new Call("purge entry", delete("/api/v1/entries/" + entry + "/permanent"), null));
		calls.add(new Call("trash", get("/api/v1/budgets/" + budget + "/entries/deleted"), null));
		calls.add(new Call("empty trash", delete("/api/v1/budgets/" + budget + "/entries/deleted"), null));
		calls.add(new Call("suggestions", get("/api/v1/budgets/" + budget + "/entries/suggestions?categoryId=" + category),
				null));
		calls.add(new Call("stats", get("/api/v1/accounts/" + account + "/stats"), null));
		calls.add(new Call("receipt", get("/api/v1/accounts/" + account + "/receipt"), null));
		return calls;
	}

	/** Bob's rows and their versions, to prove nothing changed. */
	private Map<String, Object> bobsData(TestUser bob) {
		return jdbc.queryForMap("""
				select (select count(*) from msal.budgets where user_id = :id)           as budgets,
				       (select sum(version) from msal.budgets where user_id = :id)       as budget_versions,
				       (select count(*) from msal.budget_groups where user_id = :id)     as groups,
				       (select count(*) from msal.budget_items where user_id = :id)      as items,
				       (select sum(version) from msal.budget_items where user_id = :id)  as item_versions,
				       (select count(*) from msal.accounts where user_id = :id)          as accounts,
				       (select count(*) from msal.categories where user_id = :id)        as categories,
				       (select sum(version) from msal.categories where user_id = :id)    as category_versions,
				       (select count(*) from msal.salary_cycles where user_id = :id)     as cycles,
				       (select sum(version) from msal.salary_cycles where user_id = :id) as cycle_versions,
				       (select count(*) from msal.entries where user_id = :id)           as entries,
				       (select count(*) from msal.entries where user_id = :id and deleted_at is null) as live,
				       (select sum(version) from msal.entries where user_id = :id)       as entry_versions
				""".replace(":id", "'" + bob.id() + "'"));
	}

	@Test
	void aliceGetsNotFoundForEveryOneOfBobsRecords() throws Exception {
		TestUser bob = bob();
		TestUser alice = signUp("alice");
		Map<String, Object> before = bobsData(bob);

		SoftAssertions softly = new SoftAssertions();
		for (Call call : callsOnBobsData()) {
			MockHttpServletRequestBuilder request = call.request().with(alice.token());
			if (call.body() != null) {
				request.contentType("application/json").content(jsonMapper.writeValueAsString(call.body()));
			}
			MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
			softly.assertThat(response.getStatus()).as(call.label()).isEqualTo(404);
			softly.assertThat(response.getContentAsString()).as(call.label()).contains("SYS-E004")
					.doesNotContain("عشاء بوب").doesNotContain("77.77");
		}
		softly.assertAll();
		assertThat(bobsData(bob)).as("Bob's data is unchanged").isEqualTo(before);
	}

	@Test
	void aliceCannotPointHerOwnRecordsAtBobs() throws Exception {
		bob();
		TestUser alice = signUp("alice");
		String aliceBudget = budgetOf(alice);
		String path = "/api/v1/budgets/" + aliceBudget + "/entries";

		send(alice, post(path), Map.of("amount", "1", "name", "x", "categoryId", category, "entryDate", "2026-11-11",
				"accountId", accountOf(alice))).andExpect(jsonPath("$.code").value("SRV-001-E002"));
		send(alice, post(path), Map.of("amount", "1", "name", "x", "categoryId", categoryOf(alice, "cat_food"),
				"entryDate", "2026-11-11", "accountId", account)).andExpect(jsonPath("$.code").value("SRV-001-E005"));
		int version = getJson(alice, "/api/v1/budgets/" + aliceBudget).get("version").asInt();
		send(alice, patch("/api/v1/budgets/" + aliceBudget), Map.of("lastAccountId", account, "version", version))
				.andExpect(status().isNotFound());
		call(alice, get("/api/v1/budgets/" + aliceBudget + "/entries/suggestions?categoryId=" + category))
				.andExpect(status().isNotFound());
		send(alice, put("/api/v1/budgets/" + aliceBudget + "/categories/order"), Map.of("categoryIds", List.of(category)))
				.andExpect(jsonPath("$.code").value("SRV-005-E003"));
	}

	@Test
	void listsOnlyShowTheUsersOwnData() throws Exception {
		bob();
		TestUser alice = signUp("alice");
		assertThat(getJson(alice, "/api/v1/budgets")).hasSize(1);
		assertThat(getJson(alice, "/api/v1/budgets").get(0).get("id").asString()).isNotEqualTo(budget);
		assertThat(getJson(alice, "/api/v1/accounts/" + accountOf(alice) + "/entries").get("days")).isEmpty();
	}
}
