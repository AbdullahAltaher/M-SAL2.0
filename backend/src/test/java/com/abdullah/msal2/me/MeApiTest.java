package com.abdullah.msal2.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.abdullah.msal2.IntegrationTest;

/**
 * SRV-000 and the access chain every request goes through (API.md section 1 and 2.4).
 */
class MeApiTest extends IntegrationTest {

	private static RequestPostProcessor signedIn(UUID id, String email) {
		return jwt().jwt(token -> token.subject(id.toString()).claim("email", email));
	}

	private void insertInvitation(String email, String role, Instant expiresAt) {
		UUID inviter = insertUser("inviter@test.local", "USER", "ADMIN");
		jdbc.update("""
				insert into msal.invitations (email, role_code, invited_by, expires_at)
				values (?, ?, ?, ?)
				""", email, role, inviter, Timestamp.from(expiresAt));
	}

	// ── Sign-in and first login ─────────────────────────────────

	@Test
	void noTokenIsRejectedWithRequestId() throws Exception {
		MvcResult result = mvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SYS-E001"))
				.andExpect(jsonPath("$.message").value("انتهت الجلسة، سجّل الدخول مرة أخرى."))
				.andExpect(header().exists("X-Request-Id"))
				.andReturn();
		assertThat(result.getResponse().getContentAsString())
				.contains(result.getResponse().getHeader("X-Request-Id"));
	}

	@Test
	void bootstrapAdminGetsBothRolesOnFirstLogin() throws Exception {
		UUID id = UUID.randomUUID();
		mvc.perform(get("/api/v1/me").with(signedIn(id, "Admin@Test.local")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
				.andExpect(jsonPath("$.roles[0]").value("ADMIN"))
				.andExpect(jsonPath("$.roles[1]").value("USER"))
				.andExpect(jsonPath("$.timeZone").value("Asia/Dubai"))
				.andExpect(jsonPath("$.services['SRV-001'].write").value(true))
				.andExpect(jsonPath("$.services['SRV-006'].write").value(false))
				.andExpect(jsonPath("$.services['SRV-009'].read").value(true))
				.andExpect(jsonPath("$.exportReminder.due").value(false))
				.andExpect(jsonPath("$.exportReminder.never").value(true))
				.andExpect(jsonPath("$.lastBudgetId").isNotEmpty());

		Map<String, Object> audit = jdbc.queryForMap(
				"select service_code, action, entity_type, after_data::text as after from msal.audit_log "
						+ "where user_id = ? and entity_type = 'app_user'",
				id);
		assertThat(audit).containsEntry("service_code", "SRV-000").containsEntry("action", "CREATE")
				.containsEntry("entity_type", "app_user");
		assertThat((String) audit.get("after")).contains("BOOTSTRAP");
	}

	@Test
	void bootstrapEmailIsNotAdminOnceAnotherUserExists() throws Exception {
		insertUser("someone@test.local", "USER");
		mvc.perform(get("/api/v1/me").with(signedIn(UUID.randomUUID(), ADMIN_EMAIL)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SRV-000-E001"));
	}

	@Test
	void uninvitedEmailIsRefused() throws Exception {
		mvc.perform(get("/api/v1/me").with(signedIn(UUID.randomUUID(), "stranger@test.local")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SRV-000-E001"))
				.andExpect(jsonPath("$.message").value("هذا البريد غير مدعو لاستخدام التطبيق."));
		assertThat(jdbc.queryForObject("select count(*) from msal.app_users", Integer.class)).isZero();
	}

	@Test
	void invitedEmailBecomesUserAndInvitationIsAccepted() throws Exception {
		insertInvitation("friend@test.local", "USER", Instant.now().plus(14, ChronoUnit.DAYS));
		mvc.perform(get("/api/v1/me").with(signedIn(UUID.randomUUID(), "Friend@Test.local")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles.length()").value(1))
				.andExpect(jsonPath("$.roles[0]").value("USER"))
				.andExpect(jsonPath("$.services", not(hasKey("SRV-009"))));
		assertThat(jdbc.queryForObject("select status from msal.invitations where email = 'friend@test.local'",
				String.class)).isEqualTo("ACCEPTED");
	}

	@Test
	void expiredInvitationIsRefused() throws Exception {
		insertInvitation("late@test.local", "USER", Instant.now().minus(1, ChronoUnit.DAYS));
		mvc.perform(get("/api/v1/me").with(signedIn(UUID.randomUUID(), "late@test.local")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SRV-000-E001"));
	}

	// ── Access checks ───────────────────────────────────────────

	@Test
	void disabledUserIsRefused() throws Exception {
		UUID id = insertUser("off@test.local", "USER");
		jdbc.update("update msal.app_users set status = 'DISABLED' where id = ?", id);
		mvc.perform(get("/api/v1/me").with(signedIn(id, "off@test.local")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SYS-E009"));
	}

	@Test
	void disabledServiceIsRefused() throws Exception {
		UUID id = insertUser("user@test.local", "USER");
		jdbc.update("update msal.services set enabled = false where code = 'SRV-000'");
		mvc.perform(get("/api/v1/me").with(signedIn(id, "user@test.local")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SYS-E003"));
	}

	@Test
	void writeNeedsWritePermission() throws Exception {
		jdbc.update("insert into msal.roles (code, name_ar) values ('VIEWER', 'مشاهد')");
		jdbc.update("insert into msal.role_services (role_code, service_code, can_read, can_write) "
				+ "values ('VIEWER', 'SRV-000', true, false)");
		UUID id = insertUser("viewer@test.local", "VIEWER");

		mvc.perform(get("/api/v1/me").with(signedIn(id, "viewer@test.local")))
				.andExpect(status().isOk());
		mvc.perform(patch("/api/v1/me").with(signedIn(id, "viewer@test.local"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"x\",\"version\":0}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SYS-E002"));
	}

	@Test
	void unknownApiPathIsNotFound() throws Exception {
		UUID id = insertUser("user@test.local", "USER");
		mvc.perform(get("/api/v1/nothing-here").with(signedIn(id, "user@test.local")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SYS-E004"));
	}

	// ── PATCH /me ───────────────────────────────────────────────

	@Test
	void updateChangesFieldsAndBumpsVersion() throws Exception {
		UUID id = insertUser("user@test.local", "USER");
		UUID budget = insertBudget(id, "الأساسي");
		mvc.perform(patch("/api/v1/me").with(signedIn(id, "user@test.local"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\" عبدالله \",\"timeZone\":\"Asia/Riyadh\",\"lastBudgetId\":\"" + budget
						+ "\",\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("عبدالله"))
				.andExpect(jsonPath("$.timeZone").value("Asia/Riyadh"))
				.andExpect(jsonPath("$.lastBudgetId").value(budget.toString()))
				.andExpect(jsonPath("$.version").value(1));
		assertThat(jdbc.queryForObject(
				"select count(*) from msal.audit_log where user_id = ? and action = 'UPDATE'", Integer.class, id))
				.isEqualTo(1);
	}

	@Test
	void updateWithOldVersionIsConflict() throws Exception {
		UUID id = insertUser("user@test.local", "USER");
		jdbc.update("update msal.app_users set version = 3 where id = ?", id);
		mvc.perform(patch("/api/v1/me").with(signedIn(id, "user@test.local"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"x\",\"version\":2}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SYS-E005"));
	}

	@Test
	void invalidTimeZoneIsRefused() throws Exception {
		UUID id = insertUser("user@test.local", "USER");
		mvc.perform(patch("/api/v1/me").with(signedIn(id, "user@test.local"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"timeZone\":\"Mars/Base\",\"version\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SRV-000-E002"));
	}

	@Test
	void anotherUsersBudgetIsNotFound() throws Exception {
		// Data isolation (CLAUDE.md rule 11): another user's record is 404, not 403
		UUID alice = insertUser("alice@test.local", "USER");
		UUID bob = insertUser("bob@test.local", "USER");
		UUID bobBudget = insertBudget(bob, "بوب");
		mvc.perform(patch("/api/v1/me").with(signedIn(alice, "alice@test.local"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lastBudgetId\":\"" + bobBudget + "\",\"version\":0}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SYS-E004"));
		assertThat(jdbc.queryForObject("select last_budget_id from msal.app_users where id = ?", UUID.class, alice))
				.isNull();
	}

	@Test
	void missingVersionIsInvalid() throws Exception {
		UUID id = insertUser("user@test.local", "USER");
		mvc.perform(patch("/api/v1/me").with(signedIn(id, "user@test.local"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SYS-E006"))
				.andExpect(jsonPath("$.details[0].field").value("version"));
	}

	@Test
	void brokenJsonIsInvalid() throws Exception {
		UUID id = insertUser("user@test.local", "USER");
		mvc.perform(patch("/api/v1/me").with(signedIn(id, "user@test.local"))
				.contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SYS-E006"));
	}
}
