package com.abdullah.msal2.system.audit;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.system.RequestContext;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code audit_log}: who did what, in which service, and when (PLAN.md section 11).
 * <p>
 * Rows are only inserted, never changed. The service code and request id come from the
 * request context. A hard delete must pass the deleted data as {@code before}
 * (DATABASE.md D-07). Writes join the caller's transaction, so a rolled-back change leaves
 * no audit row.
 */
@Service
public class AuditService {

	private static final String INSERT = """
			insert into msal.audit_log
			    (occurred_at, user_id, service_code, action, entity_type, entity_id, before_data, after_data, request_id)
			values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)
			""";

	private final JdbcTemplate jdbc;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public AuditService(JdbcTemplate jdbc, JsonMapper jsonMapper, Clock clock) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID userId, AuditAction action, String entityType, UUID entityId, Object before, Object after) {
		String serviceCode = RequestContext.serviceCode();
		if (serviceCode == null) {
			throw new IllegalStateException("No service code in the request context");
		}
		jdbc.update(INSERT, Timestamp.from(Instant.now(clock)), userId, serviceCode, action.name(), entityType,
				entityId, toJson(before), toJson(after), RequestContext.requestId());
	}

	private String toJson(Object data) {
		return data == null ? null : jsonMapper.writeValueAsString(data);
	}
}
