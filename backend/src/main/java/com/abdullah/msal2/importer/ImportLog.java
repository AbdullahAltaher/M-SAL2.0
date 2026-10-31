package com.abdullah.msal2.importer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.importer.ImportDtos.ImportResponse;
import com.abdullah.msal2.importer.ImportParser.Counts;
import com.abdullah.msal2.importer.ImportService.Mode;
import com.abdullah.msal2.importer.Warnings.Warning;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The {@code imports} history (without the files themselves) and {@code last_export_at}.
 */
@Repository
public class ImportLog {

	private static final String SELECT = """
			select id, file_name, mode, counts::text, warnings::text, created_at from msal.imports where user_id = ?
			""";

	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final Clock clock;

	public ImportLog(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/** The file to download, after noting the export for its reminder (R-IO-01). */
	@Transactional
	public byte[] exported(UUID userId, ObjectNode file) {
		jdbc.update("update msal.app_users set last_export_at = ? where id = ?", Timestamp.from(Instant.now(clock)),
				userId);
		return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(file);
	}

	@Transactional(readOnly = true)
	public ImportResponse get(UUID userId, UUID importId) {
		return jdbc.queryForObject(SELECT + " and id = ?", this::row, userId, importId);
	}

	/** The latest 50 imports. */
	@Transactional(readOnly = true)
	public List<ImportResponse> list(UUID userId) {
		return jdbc.query(SELECT + " order by created_at desc limit 50", this::row, userId);
	}

	private ImportResponse row(ResultSet rs, int n) throws SQLException {
		return new ImportResponse(rs.getObject("id", UUID.class), rs.getString("file_name"),
				Mode.valueOf(rs.getString("mode")), json.readValue(rs.getString("counts"), Counts.class),
				json.readValue(rs.getString("warnings"), new TypeReference<List<Warning>>() {
				}), rs.getTimestamp("created_at").toInstant());
	}
}
