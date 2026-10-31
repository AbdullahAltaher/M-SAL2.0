package com.abdullah.msal2.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.IntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * SRV-004 (R-ACC) and SRV-005 (R-CAT) through the API.
 */
class AccountCategoryApiTest extends IntegrationTest {

	@Test
	void aNewAccountNeedsANameAndBecomesTheCurrentOne() throws Exception {
		TestUser user = signUp("sara");
		String budgetId = budgetOf(user);
		send(user, post("/api/v1/budgets/" + budgetId + "/accounts"), Map.of("name", " ")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-004-E001"));
		JsonNode home = json(send(user, post("/api/v1/budgets/" + budgetId + "/accounts"), Map.of("name", "البيت"))
				.andExpect(status().isCreated()));
		assertThat(getJson(user, "/api/v1/budgets/" + budgetId).get("lastAccountId").asString())
				.isEqualTo(home.get("id").asString());
	}

	@Test
	void theLastActiveAccountCannotBeArchivedAndArchivedAccountsAreHiddenByDefault() throws Exception {
		TestUser user = signUp("sara");
		String budgetId = budgetOf(user);
		String main = accountOf(user);
		send(user, patch("/api/v1/accounts/" + main), Map.of("isArchived", true, "version", 0))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SRV-004-E002"));

		send(user, post("/api/v1/budgets/" + budgetId + "/accounts"), Map.of("name", "البيت"));
		send(user, patch("/api/v1/accounts/" + main), Map.of("isArchived", true, "version", 0))
				.andExpect(status().isOk()).andExpect(jsonPath("$.isArchived").value(true));
		assertThat(getJson(user, "/api/v1/budgets/" + budgetId + "/accounts")).hasSize(1);
		assertThat(getJson(user, "/api/v1/budgets/" + budgetId + "/accounts?includeArchived=true")).hasSize(2);

		send(user, patch("/api/v1/accounts/" + main), Map.of("name", "", "version", 1)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-004-E001"));
	}

	@Test
	void categoriesNeedANameAndAnEmojiAndAreHiddenNotDeleted() throws Exception {
		TestUser user = signUp("sara");
		String budgetId = budgetOf(user);
		String path = "/api/v1/budgets/" + budgetId + "/categories";
		send(user, post(path), Map.of("name", "", "emoji", "🐈")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-005-E001"));
		send(user, post(path), Map.of("name", "قطتي", "emoji", " ")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-005-E002"));
		JsonNode cat = json(send(user, post(path), Map.of("name", "قطتي", "emoji", "🐈")).andExpect(status().isCreated()));
		assertThat(cat.get("sortOrder").asInt()).isEqualTo(21);
		assertThat(cat.get("code").isNull()).isTrue();

		send(user, patch("/api/v1/categories/" + cat.get("id").asString()), Map.of("isHidden", true, "version", 0))
				.andExpect(status().isOk()).andExpect(jsonPath("$.isHidden").value(true));
		assertThat(getJson(user, path)).as("hidden categories are still listed").hasSize(21);
	}

	@Test
	void reorderMustListExactlyTheCurrentCategories() throws Exception {
		TestUser user = signUp("sara");
		String budgetId = budgetOf(user);
		List<String> ids = new ArrayList<>();
		getJson(user, "/api/v1/budgets/" + budgetId + "/categories").forEach(c -> ids.add(c.get("id").asString()));

		send(user, put("/api/v1/budgets/" + budgetId + "/categories/order"), Map.of("categoryIds", ids.subList(1, 20)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SRV-005-E003"));

		List<String> reversed = new ArrayList<>(ids.reversed());
		send(user, put("/api/v1/budgets/" + budgetId + "/categories/order"), Map.of("categoryIds", reversed))
				.andExpect(status().isOk());
		JsonNode after = getJson(user, "/api/v1/budgets/" + budgetId + "/categories");
		assertThat(after.get(0).get("id").asString()).isEqualTo(ids.getLast());
		assertThat(after.get(0).get("sortOrder").asInt()).isEqualTo(1);
		call(user, get("/api/v1/budgets/" + budgetId + "/categories")).andExpect(jsonPath("$[19].code").value("cat_food"));
	}
}
