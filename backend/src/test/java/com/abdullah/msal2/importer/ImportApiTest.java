package com.abdullah.msal2.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.abdullah.msal2.IntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * SRV-007 through the API against a real Postgres: R-IO-01 to R-IO-05 and DATABASE.md 6.
 */
class ImportApiTest extends IntegrationTest {

	/** An M-SAL file: one person with a group, an account with a cycle, a live and a deleted entry. */
	private static final String OLD_FILE = """
			{ "schemaVersion": 3, "currentId": "p1", "profiles": [
			  { "id": "p1", "name": "عبدالله", "data": { "monthLabel": "أكتوبر 2026", "salaryLabel": "الراتب", "salary": "~15000",
			    "blocks": [{ "id": "b1", "title": "ثابتة", "rows": [
			      { "id": "r1", "name": "إيجار", "amount": "5000", "done": true },
			      { "id": "r2", "name": "كهرباء", "amount": "~400", "done": false }] }],
			    "exp": { "accounts": [{ "id": "acc_main", "name": "مصاريفي", "archived": false,
			               "cycles": [{ "id": "c1", "startDate": "2026-11-02", "allowance": 100 }] }],
			             "lastAccountId": "acc_main", "categories": [],
			             "entries": [
			               { "id": "e1", "type": "expense", "amount": 46.25, "name": "غداء", "categoryId": "cat_food", "date": "2026-11-03", "accountId": "acc_main", "createdAt": 1793700000000 },
			               { "id": "e2", "type": "expense", "amount": 6, "name": "", "categoryId": "nope", "date": "2026-11-04", "accountId": "acc_main", "deletedAt": 1793800000000 },
			               { "id": "e3", "type": "expense", "amount": 0, "name": "صفر", "categoryId": "cat_food", "date": "2026-11-04", "accountId": "acc_main" }] } } } ] }
			""";

	private static MockMultipartFile file(String content) {
		return new MockMultipartFile("file", "ميزانية-2026-10-30.json", "application/json",
				content.getBytes(StandardCharsets.UTF_8));
	}

	private ResultActions preview(TestUser user, String content) throws Exception {
		return mvc.perform(multipart("/api/v1/imports/preview").file(file(content)).with(user.token()));
	}

	private ResultActions importFile(TestUser user, String content, String mode) throws Exception {
		return mvc.perform(multipart("/api/v1/imports").file(file(content)).param("mode", mode).with(user.token()));
	}

	private JsonNode export(TestUser user) throws Exception {
		return getJson(user, "/api/v1/export");
	}

	private int count(String table, TestUser user) {
		return jdbc.queryForObject("select count(*) from msal." + table + " where user_id = ?", Integer.class, user.id());
	}

	@Test
	void theExportIsAnMsalFileNamedByTodayAndNotesTheExport() throws Exception {
		TestUser user = signUp("sara");
		call(user, get("/api/v1/export")).andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(
						"filename*=UTF-8''%D9%85%D9%8A%D8%B2%D8%A7%D9%86%D9%8A%D8%A9-2026-11-11.json")))
				.andExpect(jsonPath("$.schemaVersion").value(3))
				.andExpect(jsonPath("$.app").value("M-SAL2.0"))
				.andExpect(jsonPath("$.currentId").value(budgetOf(user)))
				.andExpect(jsonPath("$.profiles[0].name").value("الأساسي"))
				.andExpect(jsonPath("$.profiles[0].data.blocks[0].title").value("التزامات ثابتة"))
				.andExpect(jsonPath("$.profiles[0].data.exp.accounts[0].name").value("مصاريفي"))
				.andExpect(jsonPath("$.profiles[0].data.exp.categories.length()").value(20));
		assertThat(jdbc.queryForObject("select last_export_at is not null from msal.app_users where id = ?",
				Boolean.class, user.id())).isTrue();
		call(user, get("/api/v1/me")).andExpect(jsonPath("$.exportReminder.never").value(false));
	}

	@Test
	void thePreviewCountsTheFileAndTheCurrentDataAndSavesNothing() throws Exception {
		TestUser user = signUp("sara");
		preview(user, OLD_FILE).andExpect(status().isOk())
				.andExpect(jsonPath("$.kind").value("STATE"))
				.andExpect(jsonPath("$.newer").value(false))
				.andExpect(jsonPath("$.counts.profiles").value(1))
				.andExpect(jsonPath("$.counts.blocks").value(1))
				.andExpect(jsonPath("$.counts.rows").value(2))
				.andExpect(jsonPath("$.counts.accounts").value(1))
				.andExpect(jsonPath("$.counts.cycles").value(1))
				.andExpect(jsonPath("$.counts.entries").value(2))
				.andExpect(jsonPath("$.counts.deleted").value(1))
				.andExpect(jsonPath("$.current.profiles").value(1))
				.andExpect(jsonPath("$.current.accounts").value(1))
				.andExpect(jsonPath("$.current.entries").value(0))
				.andExpect(jsonPath("$.warnings[?(@.code == 'CATEGORY_MISSING')].count").value(1))
				.andExpect(jsonPath("$.warnings[?(@.code == 'AMOUNT_INVALID')].count").value(1));
		assertThat(count("imports", user)).isZero();
		assertThat(count("budgets", user)).isEqualTo(1);
	}

	@Test
	void badFilesAreRefusedWithTheirCodes() throws Exception {
		TestUser user = signUp("sara");
		preview(user, "{not json").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-007-E001"));
		preview(user, "{\"hello\": 1}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-007-E002"));
		String big = "{\"blocks\": [], \"x\": \"" + "x".repeat(5 * 1024 * 1024) + "\"}";
		preview(user, big).andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.code").value("SRV-007-E003"));
		mvc.perform(multipart("/api/v1/imports/preview").with(user.token())).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SYS-E006"));
	}

	@Test
	void mergeAddsTheOldFileAsANewBudgetAndTwiceAddsNothing() throws Exception {
		TestUser user = signUp("sara");
		String own = budgetOf(user);
		importFile(user, OLD_FILE, "MERGE").andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("MERGE"))
				.andExpect(jsonPath("$.fileName").value("ميزانية-2026-10-30.json"))
				.andExpect(jsonPath("$.counts.entries").value(2));
		JsonNode budgets = getJson(user, "/api/v1/budgets");
		assertThat(budgets).hasSize(2);
		assertThat(budgetOf(user)).isEqualTo(own); // the open budget stays

		String imported = budgets.get(1).get("id").asString();
		JsonNode budget = getJson(user, "/api/v1/budgets/" + imported);
		assertThat(budget.get("name").asString()).isEqualTo("عبدالله");
		assertThat(budget.get("salary").get("amount").asString()).isEqualTo("15000.00");
		assertThat(budget.get("salary").get("isApprox").asBoolean()).isTrue();
		assertThat(budget.get("groups").get(0).get("items")).hasSize(2);
		assertThat(budget.get("groups").get(0).get("items").get(1).get("isApprox").asBoolean()).isTrue();
		assertThat(budget.get("groups").get(0).get("totals").get("subtotal").asString()).isEqualTo("5400.00");

		// cat_food is the template category, and "nope" went to "أخرى"
		assertThat(jdbc.queryForObject("""
				select c.code from msal.entries e join msal.categories c on c.id = e.category_id
				where e.user_id = ? and e.name = 'غداء'
				""", String.class, user.id())).isEqualTo("cat_food");
		assertThat(jdbc.queryForObject("""
				select c.code from msal.entries e join msal.categories c on c.id = e.category_id
				where e.user_id = ? and e.deleted_at is not null
				""", String.class, user.id())).isEqualTo("cat_other");
		assertThat(jdbc.queryForObject("select count(*) from msal.categories where budget_id = ?::uuid",
				Integer.class, imported)).isEqualTo(20);

		int entries = count("entries", user);
		int cycles = count("salary_cycles", user);
		int items = count("budget_items", user);
		importFile(user, OLD_FILE, "MERGE").andExpect(status().isOk());
		assertThat(getJson(user, "/api/v1/budgets")).hasSize(2);
		assertThat(count("entries", user)).isEqualTo(entries).isEqualTo(2);
		assertThat(count("salary_cycles", user)).isEqualTo(cycles).isEqualTo(1);
		assertThat(count("budget_items", user)).isEqualTo(items);
		call(user, get("/api/v1/imports")).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].warnings[?(@.code == 'AMOUNT_INVALID')].count").value(1));
	}

	@Test
	void mergeKeepsWhatIsSavedAndSkipsACycleOnASavedStartDate() throws Exception {
		TestUser user = signUp("sara");
		importFile(user, OLD_FILE, "MERGE").andExpect(status().isOk());
		String imported = getJson(user, "/api/v1/budgets").get(1).get("id").asString();
		JsonNode item = getJson(user, "/api/v1/budgets/" + imported).get("groups").get(0).get("items").get(0);
		send(user, patch("/api/v1/budget-items/" + item.get("id").asString()),
				Map.of("name", "إيجار جديد", "version", item.get("version").asInt())).andExpect(status().isOk());

		String changed = OLD_FILE.replace("\"c1\", \"startDate\": \"2026-11-02\"", "\"c9\", \"startDate\": \"2026-11-02\"")
				.replace("\"r1\", \"name\": \"إيجار\"", "\"r1\", \"name\": \"قديم\"")
				.replace("{ \"id\": \"e1\"", "{ \"id\": \"e4\", \"type\": \"expense\", \"amount\": 9, \"name\": \"جديد\", \"categoryId\": \"cat_food\", \"date\": \"2026-11-05\", \"accountId\": \"acc_main\" },\n{ \"id\": \"e1\"");
		importFile(user, changed, "MERGE").andExpect(status().isOk());
		JsonNode after = getJson(user, "/api/v1/budgets/" + imported).get("groups").get(0).get("items").get(0);
		assertThat(after.get("name").asString()).isEqualTo("إيجار جديد");
		assertThat(count("salary_cycles", user)).isEqualTo(1);
		assertThat(count("entries", user)).isEqualTo(3);
	}

	@Test
	void anExportImportedWithReplaceGivesTheSameDataAndTheBeforeDataIsAudited() throws Exception {
		TestUser user = signUp("sara");
		importFile(user, OLD_FILE, "MERGE").andExpect(status().isOk());
		JsonNode first = export(user);

		importFile(user, OLD_FILE.replace("\"عبدالله\"", "\"تغيّر\""), "MERGE").andExpect(status().isOk());
		String exported = jsonMapper.writeValueAsString(first);
		importFile(user, exported, "REPLACE").andExpect(status().isOk());
		JsonNode second = export(user);

		((tools.jackson.databind.node.ObjectNode) first).remove("exportedAt");
		((tools.jackson.databind.node.ObjectNode) second).remove("exportedAt");
		assertThat(second).isEqualTo(first);
		assertThat(jdbc.queryForObject("select count(*) from msal.categories where user_id = ? and code is not null",
				Integer.class, user.id())).isEqualTo(40);
		assertThat(jdbc.queryForObject("""
				select count(*) from msal.audit_log where user_id = ? and action = 'IMPORT' and before_data is not null
				""", Integer.class, user.id())).isEqualTo(3);
		assertThat(jdbc.queryForObject("select raw_data->>'app' from msal.imports where mode = 'REPLACE' and user_id = ?",
				String.class, user.id())).isEqualTo("M-SAL2.0");
	}

	@Test
	void replaceTakesTheFileButKeepsExpensesWhenTheFileHasNone() throws Exception {
		TestUser user = signUp("sara");
		String own = budgetOf(user);
		send(user, post("/api/v1/budgets/" + own + "/entries"),
				Map.of("amount", "10", "name", "قهوة", "categoryId", categoryOf(user, "cat_coffee"),
						"entryDate", "2026-11-10", "accountId", accountOf(user))).andExpect(status().isCreated());

		String noExp = """
				{ "schemaVersion": 2, "currentId": "%s", "profiles": [
				  { "id": "%s", "name": "من الملف", "data": { "salary": "9000", "blocks": [{ "id": "x", "title": "جديدة", "rows": [] }] } },
				  { "id": "p2", "name": "سارة", "data": { "blocks": [] } } ] }
				""".formatted(own, own);
		importFile(user, noExp, "REPLACE").andExpect(status().isOk());
		JsonNode budgets = getJson(user, "/api/v1/budgets");
		assertThat(budgets).hasSize(2);
		JsonNode budget = getJson(user, "/api/v1/budgets/" + own);
		assertThat(budget.get("name").asString()).isEqualTo("من الملف");
		assertThat(budget.get("groups")).hasSize(1);
		assertThat(budget.get("groups").get(0).get("title").asString()).isEqualTo("جديدة");
		assertThat(jdbc.queryForObject("select count(*) from msal.entries where budget_id = ?::uuid and name = 'قهوة'",
				Integer.class, own)).isEqualTo(1);
		// the new person gets the default account and categories
		String sara = budgets.get(1).get("id").asString();
		assertThat(getJson(user, "/api/v1/budgets/" + sara + "/accounts")).hasSize(1);
		assertThat(getJson(user, "/api/v1/budgets/" + sara + "/categories")).hasSize(20);
	}

	@Test
	void replaceRemovesBudgetsNotInTheFile() throws Exception {
		TestUser user = signUp("sara");
		String own = budgetOf(user);
		importFile(user, OLD_FILE, "REPLACE").andExpect(status().isOk());
		JsonNode budgets = getJson(user, "/api/v1/budgets");
		assertThat(budgets).hasSize(1);
		assertThat(budgets.get(0).get("name").asString()).isEqualTo("عبدالله");
		assertThat(budgetOf(user)).isEqualTo(budgets.get(0).get("id").asString()).isNotEqualTo(own);
		assertThat(jdbc.queryForObject("select deleted_at is not null from msal.budgets where id = ?::uuid",
				Boolean.class, own)).isTrue();
	}

	@Test
	void aOnePersonFileGoesIntoTheOpenBudget() throws Exception {
		TestUser user = signUp("sara");
		String own = budgetOf(user);
		String single = """
				{ "monthLabel": "سبتمبر 2026", "salary": "8000", "blocks": [
				  { "id": "b1", "title": "قديمة", "rows": [{ "id": "r1", "name": "سيارة", "amount": "2500" }] }] }
				""";
		preview(user, single).andExpect(status().isOk())
				.andExpect(jsonPath("$.kind").value("SINGLE"))
				.andExpect(jsonPath("$.target.id").value(own))
				.andExpect(jsonPath("$.target.name").value("الأساسي"));

		importFile(user, single, "MERGE").andExpect(status().isOk());
		JsonNode merged = getJson(user, "/api/v1/budgets/" + own);
		assertThat(merged.get("groups")).hasSize(2);
		assertThat(merged.get("salary").path("amount").asString("")).isNotEqualTo("8000.00"); // the saved salary wins

		importFile(user, single, "REPLACE").andExpect(status().isOk());
		JsonNode replaced = getJson(user, "/api/v1/budgets/" + own);
		assertThat(replaced.get("groups")).hasSize(1);
		assertThat(replaced.get("monthLabel").asString()).isEqualTo("سبتمبر 2026");
		assertThat(replaced.get("salary").get("amount").asString()).isEqualTo("8000.00");
		assertThat(getJson(user, "/api/v1/budgets")).hasSize(1);
		assertThat(getJson(user, "/api/v1/budgets/" + own + "/accounts")).hasSize(1);
	}

	@Test
	void importsAndExportsNeverReachAnotherUser() throws Exception {
		TestUser sara = signUp("sara");
		TestUser omar = signUp("omar");
		importFile(sara, OLD_FILE, "MERGE").andExpect(status().isOk());
		JsonNode saraFile = export(sara);

		// Omar imports Sara's file: he gets copies, and Sara's records are untouched
		importFile(omar, jsonMapper.writeValueAsString(saraFile), "REPLACE").andExpect(status().isOk());
		assertThat(getJson(omar, "/api/v1/budgets")).hasSize(2);
		assertThat(count("entries", omar)).isEqualTo(2);
		assertThat(jdbc.queryForObject("""
				select count(*) from msal.budgets a join msal.budgets b on a.id = b.id
				where a.user_id = ? and b.user_id = ?
				""", Integer.class, sara.id(), omar.id())).isZero();
		((tools.jackson.databind.node.ObjectNode) saraFile).remove("exportedAt");
		JsonNode saraAgain = export(sara);
		((tools.jackson.databind.node.ObjectNode) saraAgain).remove("exportedAt");
		assertThat(saraAgain).isEqualTo(saraFile);
		assertThat(getJson(omar, "/api/v1/imports")).hasSize(1);
		assertThat(getJson(sara, "/api/v1/imports")).hasSize(1);
		assertThat(export(omar).get("profiles").toString()).doesNotContain(budgetOf(sara));
	}
}
