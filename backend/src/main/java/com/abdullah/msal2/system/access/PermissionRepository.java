package com.abdullah.msal2.system.access;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads {@code services} and {@code role_services} for a user's roles (PLAN.md section 5).
 */
@Repository
public class PermissionRepository {

	/** What a user may do in one service. */
	public record ServiceAccess(boolean enabled, boolean canRead, boolean canWrite) {
	}

	private static final String ACCESS = """
			select s.code,
			       s.enabled,
			       coalesce(bool_or(rs.can_read), false)  as can_read,
			       coalesce(bool_or(rs.can_write), false) as can_write
			from msal.services s
			left join msal.role_services rs
			       on rs.service_code = s.code
			      and rs.role_code in (select ur.role_code from msal.user_roles ur where ur.user_id = ?)
			""";

	private final JdbcTemplate jdbc;

	public PermissionRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Empty if the service code does not exist. */
	public Optional<ServiceAccess> access(UUID userId, String serviceCode) {
		return jdbc.query(ACCESS + " where s.code = ? group by s.code, s.enabled",
				(rs, i) -> new ServiceAccess(rs.getBoolean("enabled"), rs.getBoolean("can_read"),
						rs.getBoolean("can_write")),
				userId, serviceCode).stream().findFirst();
	}

	/** Enabled services the user can read or write, by code (for {@code GET /me}). */
	public Map<String, ServiceAccess> permitted(UUID userId) {
		Map<String, ServiceAccess> result = new LinkedHashMap<>();
		jdbc.query(ACCESS + " group by s.code, s.enabled order by s.code", rs -> {
			ServiceAccess access = new ServiceAccess(rs.getBoolean("enabled"), rs.getBoolean("can_read"),
					rs.getBoolean("can_write"));
			if (access.enabled() && (access.canRead() || access.canWrite())) {
				result.put(rs.getString("code"), access);
			}
		}, userId);
		return result;
	}
}
