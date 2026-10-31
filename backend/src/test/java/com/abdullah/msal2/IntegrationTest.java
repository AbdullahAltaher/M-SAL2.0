package com.abdullah.msal2;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for tests against the whole app and a real Postgres. All subclasses share one
 * Spring context and one container. Every test starts with no users and no user data.
 */
@SpringBootTest(properties = "msal.bootstrap-admin-email=" + IntegrationTest.ADMIN_EMAIL)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

	protected static final String ADMIN_EMAIL = "admin@test.local";

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@BeforeEach
	void resetData() {
		// Every user table references app_users, so this empties all user data
		jdbc.execute("truncate msal.app_users cascade");
		jdbc.update("update msal.services set enabled = true");
		jdbc.update("delete from msal.role_services where role_code not in ('USER', 'ADMIN')");
		jdbc.update("delete from msal.roles where code not in ('USER', 'ADMIN')");
	}

	/** Inserts a user directly, without the sign-in flow. */
	protected UUID insertUser(String email, String... roles) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into msal.app_users (id, email) values (?, ?)", id, email);
		for (String role : roles) {
			jdbc.update("insert into msal.user_roles (user_id, role_code) values (?, ?)", id, role);
		}
		return id;
	}

	/** Inserts a budget for a user and returns its id. */
	protected UUID insertBudget(UUID userId, String name) {
		return jdbc.queryForObject("insert into msal.budgets (user_id, name) values (?, ?) returning id",
				UUID.class, userId, name);
	}
}
