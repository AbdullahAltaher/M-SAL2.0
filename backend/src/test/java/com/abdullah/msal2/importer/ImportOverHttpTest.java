package com.abdullah.msal2.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.abdullah.msal2.FixedClockConfiguration;
import com.abdullah.msal2.TestcontainersConfiguration;
import com.abdullah.msal2.push.WebPush;
import com.abdullah.msal2.users.SupabaseAuthAdmin;

/**
 * The import through a real Tomcat, as Safari on the iPhone sends it: MockMvc skips Tomcat's
 * own multipart parsing, which is where an import from the phone failed with SYS-E006.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "msal.bootstrap-admin-email=admin@test.local", "msal.push.reminder-cron=-" })
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class ImportOverHttpTest {

	private static final String TOKEN = "test-token";
	private static final String FILE = """
			{ "profiles": [ { "id": "p1", "name": "الأساسي", "data": { "salary": "15000", "blocks": [
			  { "id": "b1", "title": "التزامات ثابتة", "rows": [ { "id": "r1", "name": "إيجار", "amount": "5000", "done": true } ] } ],
			  "exp": { "accounts": [ { "id": "acc_main", "name": "مصاريفي", "archived": false, "cycles": [] } ],
			    "lastAccountId": "acc_main", "categories": [], "entries": [
			    { "id": "e1", "type": "expense", "amount": 46.25, "name": "غداء", "categoryId": "cat_food",
			      "date": "2026-11-03", "accountId": "acc_main", "createdAt": 1793700000000 } ] } } } ],
			  "currentId": "p1", "schemaVersion": 3 }
			""";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	JwtDecoder jwtDecoder;

	@MockitoBean
	SupabaseAuthAdmin authAdmin;

	@MockitoBean
	WebPush webPush;

	private final HttpClient http = HttpClient.newHttpClient();
	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void signedIn() throws Exception {
		jdbc.execute("truncate msal.app_users cascade");
		Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "ES256").subject(userId.toString())
				.claim("email", "admin@test.local").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
				.build();
		when(jwtDecoder.decode(anyString())).thenReturn(jwt);
		assertThat(send(HttpRequest.newBuilder(uri("/api/v1/me")).GET()).statusCode()).isEqualTo(200);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return http.send(request.header("Authorization", "Bearer " + TOKEN).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	/** The body Safari builds: UTF-8 file name in the part header, as WebKit sends it. */
	private static Map.Entry<String, byte[]> safariMultipart(String fileName, String contentType) {
		String boundary = "----WebKitFormBoundaryq8Kx2nZaB0fYv7Tp";
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
				+ "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.writeBytes(FILE.getBytes(StandardCharsets.UTF_8));
		body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return Map.entry("multipart/form-data; boundary=" + boundary, body.toByteArray());
	}

	private HttpResponse<String> post(String path, String fileName, String contentType) throws Exception {
		var multipart = safariMultipart(fileName, contentType);
		return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", multipart.getKey())
				.POST(HttpRequest.BodyPublishers.ofByteArray(multipart.getValue())));
	}

	@Test
	void anArabicFileNameFromTheIphoneIsPreviewedAndImported() throws Exception {
		for (String type : new String[] { "application/json", "application/octet-stream", "" }) {
			HttpResponse<String> preview = post("/api/v1/imports/preview", "__ميزانية-2026-11-01_ (1).json", type);
			assertThat(preview.statusCode()).as("preview, type [%s]: %s", type, preview.body()).isEqualTo(200);
		}
		HttpResponse<String> imported = post("/api/v1/imports?mode=REPLACE", "__ميزانية-2026-11-01_ (1).json",
				"application/json");
		assertThat(imported.statusCode()).as(imported.body()).isEqualTo(200);
		assertThat(imported.body()).contains("\"fileName\":\"__ميزانية-2026-11-01_ (1).json\"");
	}
}
