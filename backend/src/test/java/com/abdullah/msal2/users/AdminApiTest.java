package com.abdullah.msal2.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.abdullah.msal2.IntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * SRV-009 through the API (API.md 2.3). Supabase Auth is a mock.
 */
class AdminApiTest extends IntegrationTest {

	@Test
	void onlyAdminsReachTheService() throws Exception {
		signUpAdmin();
		TestUser user = signUp("sara");
		call(user, get("/api/v1/admin/users")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SYS-E002"));
	}

	@Test
	void inviteCreatesAConfirmedSupabaseUserAndThenTheFriendCanSignIn() throws Exception {
		TestUser admin = signUpAdmin();
		UUID authId = UUID.randomUUID();
		when(authAdmin.createUser("friend@test.local")).thenReturn(authId);

		send(admin, post("/api/v1/admin/invitations"), Map.of("email", " Friend@Test.local "))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("friend@test.local"))
				.andExpect(jsonPath("$.roleCode").value("USER"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.expiresAt").value("2026-11-25T08:00:00Z"));
		send(admin, post("/api/v1/admin/invitations"), Map.of("email", "friend@test.local"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SRV-009-E002"));
		send(admin, post("/api/v1/admin/invitations"), Map.of("email", ADMIN_EMAIL))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SRV-009-E001"));
		send(admin, post("/api/v1/admin/invitations"), Map.of("email", "not-an-email"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS-E006"));

		TestUser friend = new TestUser(authId, "friend@test.local");
		call(friend, get("/api/v1/me")).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("USER"));
		call(admin, get("/api/v1/admin/invitations")).andExpect(jsonPath("$[0].status").value("ACCEPTED"));
	}

	@Test
	void aSupabaseFailureSavesNothing() throws Exception {
		TestUser admin = signUpAdmin();
		when(authAdmin.createUser(anyString())).thenThrow(new SupabaseAuthAdmin.AuthAdminException("down"));
		send(admin, post("/api/v1/admin/invitations"), Map.of("email", "friend@test.local"))
				.andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("SRV-009-E004"));
		assertThat(jdbc.queryForObject("select count(*) from msal.invitations", Integer.class)).isZero();
	}

	@Test
	void revokingAnUnusedInvitationDeletesTheSupabaseUser() throws Exception {
		TestUser admin = signUpAdmin();
		UUID authId = UUID.randomUUID();
		when(authAdmin.createUser("friend@test.local")).thenReturn(authId);
		String id = json(send(admin, post("/api/v1/admin/invitations"), Map.of("email", "friend@test.local")))
				.get("id").asString();

		call(admin, delete("/api/v1/admin/invitations/" + id)).andExpect(status().isNoContent());
		verify(authAdmin).deleteUser(authId);
		call(admin, get("/api/v1/admin/invitations")).andExpect(jsonPath("$[0].status").value("REVOKED"));
		call(admin, delete("/api/v1/admin/invitations/" + id)).andExpect(status().isBadRequest());
		// The friend can no longer sign in
		call(new TestUser(authId, "friend@test.local"), get("/api/v1/me")).andExpect(status().isForbidden());
	}

	@Test
	void adminsCannotLockThemselvesOut() throws Exception {
		TestUser admin = signUpAdmin();
		JsonNode me = getJson(admin, "/api/v1/me");
		String path = "/api/v1/admin/users/" + admin.id();
		int version = me.get("version").asInt();
		send(admin, patch(path), Map.of("status", "DISABLED", "version", version)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SRV-009-E003"));
		send(admin, patch(path), Map.of("roles", List.of("USER"), "version", version)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SRV-009-E003"));
		verify(authAdmin, never()).deleteUser(admin.id());
	}

	@Test
	void disablingAUserOrAServiceTakesEffect() throws Exception {
		TestUser admin = signUpAdmin();
		TestUser user = signUp("sara");
		int version = getJson(user, "/api/v1/me").get("version").asInt();
		send(admin, patch("/api/v1/admin/users/" + user.id()), Map.of("status", "DISABLED", "version", version))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
		call(user, get("/api/v1/me")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SYS-E009"));

		send(admin, patch("/api/v1/admin/services/SRV-006"), Map.of("enabled", false)).andExpect(status().isOk());
		call(admin, get("/api/v1/me")).andExpect(jsonPath("$.services['SRV-006']").doesNotExist());
		call(admin, get("/api/v1/accounts/" + accountOf(admin) + "/stats")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SYS-E003"));
		send(admin, patch("/api/v1/admin/services/SRV-009"), Map.of("enabled", false)).andExpect(status().isBadRequest());
	}

	@Test
	void theActivityLogHasNoFinancialData() throws Exception {
		TestUser admin = signUpAdmin();
		signUp("sara");
		JsonNode page = getJson(admin, "/api/v1/admin/audit-log?size=5");
		assertThat(page.get("total").asLong()).isGreaterThan(0);
		assertThat(page.get("items").size()).isLessThanOrEqualTo(5);
		assertThat(page.get("items").get(0).has("beforeData")).isFalse();
		assertThat(page.get("items").get(0).has("afterData")).isFalse();
		assertThat(getJson(admin, "/api/v1/admin/audit-log?service=SRV-002").get("items"))
				.allSatisfy(i -> assertThat(i.get("serviceCode").asString()).isEqualTo("SRV-002"));
	}

	@Test
	void invitationsHaveTheStricterRateLimit() throws Exception {
		TestUser admin = signUpAdmin();
		when(authAdmin.createUser(anyString())).thenReturn(UUID.randomUUID());
		for (int i = 0; i < 10; i++) {
			send(admin, post("/api/v1/admin/invitations"), Map.of("email", "f" + i + "@test.local"))
					.andExpect(status().isCreated());
		}
		send(admin, post("/api/v1/admin/invitations"), Map.of("email", "f10@test.local"))
				.andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SYS-E007"));
	}
}
