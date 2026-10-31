package com.abdullah.msal2;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.abdullah.msal2.push.WebPush;
import com.abdullah.msal2.users.SupabaseAuthAdmin;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for tests against the whole app and a real Postgres. All subclasses share one
 * Spring context and one container. Every test starts with no users and no user data.
 * <p>
 * "Now" is fixed at 2026-11-11 08:00 UTC (12:00 in Dubai), the date of the examples in
 * BUSINESS_RULES.md. Supabase Auth and Web Push are mocks: tests never call the real services.
 */
@SpringBootTest(properties = { "msal.bootstrap-admin-email=" + IntegrationTest.ADMIN_EMAIL,
		"msal.push.reminder-cron=-" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
public abstract class IntegrationTest {

	protected static final String ADMIN_EMAIL = "admin@test.local";

	protected static final Instant NOW = FixedClockConfiguration.NOW;

	/** A signed-in user for requests. */
	public record TestUser(UUID id, String email) {

		public RequestPostProcessor token() {
			return jwt().jwt(t -> t.subject(id.toString()).claim("email", email));
		}
	}

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected JsonMapper jsonMapper;

	@MockitoBean
	protected SupabaseAuthAdmin authAdmin;

	/** Push services are never called; tests run the daily reminder themselves */
	@MockitoBean
	protected WebPush webPush;

	@BeforeEach
	void resetData() {
		// Every user table references app_users, so this empties all user data
		jdbc.execute("truncate msal.app_users cascade");
		jdbc.update("update msal.services set enabled = true");
		jdbc.update("delete from msal.role_services where role_code not in ('USER', 'ADMIN')");
		jdbc.update("delete from msal.roles where code not in ('USER', 'ADMIN')");
	}

	/** Inserts a user directly, without the sign-in flow (and so without a budget). */
	protected UUID insertUser(String email, String... roles) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into msal.app_users (id, email) values (?, ?)", id, email);
		for (String role : roles) {
			jdbc.update("insert into msal.user_roles (user_id, role_code) values (?, ?)", id, role);
		}
		return id;
	}

	/** Inserts a bare budget for a user and returns its id. */
	protected UUID insertBudget(UUID userId, String name) {
		return jdbc.queryForObject("insert into msal.budgets (user_id, name) values (?, ?) returning id",
				UUID.class, userId, name);
	}

	/** The bootstrap admin, signed in for the first time (gets USER and ADMIN and a first budget). */
	protected TestUser signUpAdmin() throws Exception {
		TestUser admin = new TestUser(UUID.randomUUID(), ADMIN_EMAIL);
		mvc.perform(get("/api/v1/me").with(admin.token())).andExpect(status().isOk());
		return admin;
	}

	/** An invited user, signed in for the first time, with the default budget "الأساسي". */
	protected TestUser signUp(String name) throws Exception {
		String email = name + "@test.local";
		UUID inviter = jdbc.query("select id from msal.app_users order by created_at limit 1",
				(rs, i) -> rs.getObject(1, UUID.class)).stream().findFirst()
				.orElseGet(() -> insertUser("inviter@test.local", "USER", "ADMIN"));
		jdbc.update("insert into msal.invitations (email, role_code, invited_by, expires_at) values (?, 'USER', ?, ?)",
				email, inviter, Timestamp.from(NOW.plusSeconds(86_400)));
		TestUser user = new TestUser(UUID.randomUUID(), email);
		mvc.perform(get("/api/v1/me").with(user.token())).andExpect(status().isOk());
		return user;
	}

	protected ResultActions call(TestUser user, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.with(user.token()));
	}

	protected ResultActions send(TestUser user, MockHttpServletRequestBuilder request, Object body) throws Exception {
		return mvc.perform(request.with(user.token()).contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

	protected JsonNode json(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	protected JsonNode getJson(TestUser user, String path) throws Exception {
		return json(call(user, get(path)).andExpect(status().isOk()));
	}

	/** The user's open budget id. */
	protected String budgetOf(TestUser user) throws Exception {
		return getJson(user, "/api/v1/me").get("lastBudgetId").asString();
	}

	/** The first account of the user's open budget. */
	protected String accountOf(TestUser user) throws Exception {
		return getJson(user, "/api/v1/budgets/" + budgetOf(user) + "/accounts").get(0).get("id").asString();
	}

	/** A category of the user's open budget by its template code, such as {@code cat_food}. */
	protected String categoryOf(TestUser user, String code) throws Exception {
		for (JsonNode c : getJson(user, "/api/v1/budgets/" + budgetOf(user) + "/categories")) {
			if (code.equals(c.path("code").asString(""))) {
				return c.get("id").asString();
			}
		}
		throw new IllegalArgumentException("No category " + code);
	}
}
