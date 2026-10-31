package com.abdullah.msal2.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.abdullah.msal2.IntegrationTest;

/**
 * API.md section 7: requests per minute, body size, and Swagger off outside development.
 */
class RequestLimitsTest extends IntegrationTest {

	@Test
	void the121stRequestInAMinuteIsRefused() throws Exception {
		TestUser user = signUp("sara"); // the first request of the minute
		int sent = 1;
		int status = 200;
		while (status == 200 && sent < 200) {
			status = call(user, get("/api/v1/me")).andReturn().getResponse().getStatus();
			sent++;
		}
		assertThat(sent).as("refused at request").isEqualTo(121);
		assertThat(status).isEqualTo(429);
		call(user, get("/api/v1/me")).andExpect(jsonPath("$.code").value("SYS-E007"))
				.andExpect(jsonPath("$.message").value("طلبات كثيرة، حاول بعد قليل."));
	}

	@Test
	void aBodyOverOneMegabyteIsRefused() throws Exception {
		TestUser user = signUp("sara");
		String big = "{\"name\":\"" + "x".repeat(1024 * 1024) + "\"}";
		mvc.perform(post("/api/v1/budgets").with(user.token()).contentType(MediaType.APPLICATION_JSON).content(big))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SYS-E006"));
	}

	@Test
	void swaggerIsOffByDefault() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
	}
}
