package com.abdullah.msal2.users;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.persistence.Versions;
import com.abdullah.msal2.system.web.Patch;
import com.abdullah.msal2.users.AdminDtos.AuditEntry;
import com.abdullah.msal2.users.AdminDtos.AuditPage;
import com.abdullah.msal2.users.AdminDtos.InvitationResponse;
import com.abdullah.msal2.users.AdminDtos.ServiceResponse;
import com.abdullah.msal2.users.AdminDtos.UpdateUserRequest;
import com.abdullah.msal2.users.AdminDtos.UserResponse;

/**
 * SRV-009: users, invitations, services and the activity log (ADMIN only, API.md 2.3).
 */
@Service
public class AdminService {

	/** How long an invitation is valid (API.md 2.3). */
	static final Duration INVITATION_TTL = Duration.ofDays(14);

	/** Services that must stay on, or the admin could lock everyone out. */
	private static final Set<String> ALWAYS_ON = Set.of("SRV-000", "SRV-009");

	private static final int MAX_PAGE_SIZE = 100;

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final AppUserRepository users;
	private final InvitationRepository invitations;
	private final SupabaseAuthAdmin authAdmin;
	private final AuditService audit;
	private final JdbcTemplate jdbc;
	private final Clock clock;

	public AdminService(AppUserRepository users, InvitationRepository invitations, SupabaseAuthAdmin authAdmin,
			AuditService audit, JdbcTemplate jdbc, Clock clock) {
		this.users = users;
		this.invitations = invitations;
		this.authAdmin = authAdmin;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ── Users ───────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<UserResponse> users() {
		return users.findAll().stream().sorted(Comparator.comparing(AppUser::getCreatedAt)).map(UserResponse::of).toList();
	}

	/** Changes a user's status or roles. Admins cannot disable themselves or drop their own ADMIN role. */
	@Transactional
	public UserResponse updateUser(UUID adminId, UUID userId, Patch<UpdateUserRequest> patch) {
		AppUser user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
		UpdateUserRequest request = patch.value();
		Versions.check(user, request.version());
		Map<String, Object> before = snapshot(user);

		if (request.status() != null) {
			if (userId.equals(adminId) && request.status() == UserStatus.DISABLED) {
				throw new ApiException(ErrorCode.SRV_009_E003);
			}
			user.setStatus(request.status());
		}
		if (request.roles() != null) {
			Set<String> roles = new HashSet<>(request.roles());
			if (roles.isEmpty() || !knownRoles().containsAll(roles)) {
				throw new ApiException(ErrorCode.SYS_E006);
			}
			if (userId.equals(adminId) && !roles.contains(Roles.ADMIN)) {
				throw new ApiException(ErrorCode.SRV_009_E003);
			}
			user.setRoles(roles);
		}
		AppUser saved = users.saveAndFlush(user);
		audit.record(adminId, AuditAction.UPDATE, "app_user", userId, before, snapshot(saved));
		return UserResponse.of(saved);
	}

	// ── Invitations ─────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<InvitationResponse> invitations() {
		Instant now = Instant.now(clock);
		return invitations.findAll().stream()
				.sorted(Comparator.comparing(Invitation::getCreatedAt).reversed())
				.map(i -> toResponse(i, now))
				.toList();
	}

	/**
	 * Saves a PENDING invitation and creates the Supabase user, confirmed and without a password
	 * (API.md 2.3). If Supabase fails, nothing is saved ({@code SRV-009-E004}).
	 */
	@Transactional
	public InvitationResponse invite(UUID adminId, String rawEmail, String rawRole) {
		String email = rawEmail.trim().toLowerCase(Locale.ROOT);
		if (!EMAIL.matcher(email).matches()) {
			throw new ApiException(ErrorCode.SYS_E006);
		}
		String role = rawRole == null || rawRole.isBlank() ? Roles.USER : rawRole;
		if (!knownRoles().contains(role)) {
			throw new ApiException(ErrorCode.SYS_E006);
		}
		if (users.existsByEmailIgnoreCase(email)) {
			throw new ApiException(ErrorCode.SRV_009_E001);
		}
		Instant now = Instant.now(clock);
		var pending = invitations.findFirstByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING);
		if (pending.isPresent()) {
			if (!pending.get().isExpired(now)) {
				throw new ApiException(ErrorCode.SRV_009_E002);
			}
			pending.get().expire();
			invitations.saveAndFlush(pending.get());
		}

		Invitation invitation = invitations.saveAndFlush(new Invitation(email, role, adminId, now.plus(INVITATION_TTL)));
		try {
			invitation.setAuthUserId(authAdmin.createUser(email));
		}
		catch (SupabaseAuthAdmin.AuthAdminException e) {
			throw new ApiException(ErrorCode.SRV_009_E004);
		}
		invitations.saveAndFlush(invitation);
		audit.record(adminId, AuditAction.CREATE, "invitation", invitation.getId(), null,
				Map.of("email", email, "roleCode", role));
		return toResponse(invitation, now);
	}

	/** Revokes a pending invitation, and deletes its Supabase user if they never signed in (API.md 2.3). */
	@Transactional
	public void revoke(UUID adminId, UUID invitationId) {
		Invitation invitation = invitations.findById(invitationId)
				.orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
		if (invitation.getStatus() != InvitationStatus.PENDING) {
			throw new ApiException(ErrorCode.SYS_E006);
		}
		invitation.revoke();
		invitations.saveAndFlush(invitation);
		if (invitation.getAuthUserId() != null && !users.existsByEmailIgnoreCase(invitation.getEmail())) {
			try {
				authAdmin.deleteUser(invitation.getAuthUserId());
			}
			catch (SupabaseAuthAdmin.AuthAdminException e) {
				throw new ApiException(ErrorCode.SRV_009_E004);
			}
		}
		audit.record(adminId, AuditAction.UPDATE, "invitation", invitationId, Map.of("status", "PENDING"),
				Map.of("status", "REVOKED"));
	}

	// ── Services ────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<ServiceResponse> services() {
		return jdbc.query("select code, name_ar, name_en, enabled from msal.services order by code",
				(rs, i) -> new ServiceResponse(rs.getString("code"), rs.getString("name_ar"), rs.getString("name_en"),
						rs.getBoolean("enabled")));
	}

	/** Turns a service on or off without a code change (PLAN.md section 5). */
	@Transactional
	public ServiceResponse updateService(UUID adminId, String code, boolean enabled) {
		List<ServiceResponse> found = services().stream().filter(s -> s.code().equals(code)).toList();
		if (found.isEmpty()) {
			throw new ApiException(ErrorCode.SYS_E004);
		}
		if (!enabled && ALWAYS_ON.contains(code)) {
			throw new ApiException(ErrorCode.SYS_E006);
		}
		jdbc.update("update msal.services set enabled = ?, updated_at = ? where code = ?", enabled,
				Timestamp.from(Instant.now(clock)), code);
		ServiceResponse before = found.getFirst();
		audit.record(adminId, AuditAction.UPDATE, "service", null, Map.of("code", code, "enabled", before.enabled()),
				Map.of("code", code, "enabled", enabled));
		return new ServiceResponse(code, before.nameAr(), before.nameEn(), enabled);
	}

	// ── Activity log ────────────────────────────────────────────

	/** Newest first. The data columns ({@code before_data}, {@code after_data}) are never returned. */
	@Transactional(readOnly = true)
	public AuditPage auditLog(UUID userId, String service, Instant from, Instant to, int page, int size) {
		int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		int pageIndex = Math.max(0, page);
		StringBuilder where = new StringBuilder(" where 1 = 1");
		List<Object> args = new ArrayList<>();
		if (userId != null) {
			where.append(" and a.user_id = ?");
			args.add(userId);
		}
		if (service != null && !service.isBlank()) {
			where.append(" and a.service_code = ?");
			args.add(service);
		}
		if (from != null) {
			where.append(" and a.occurred_at >= ?");
			args.add(Timestamp.from(from));
		}
		if (to != null) {
			where.append(" and a.occurred_at < ?");
			args.add(Timestamp.from(to));
		}
		Long total = jdbc.queryForObject("select count(*) from msal.audit_log a" + where, Long.class, args.toArray());

		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(pageSize);
		pageArgs.add((long) pageIndex * pageSize);
		List<AuditEntry> items = jdbc.query("""
				select a.id, a.occurred_at, a.user_id, u.email, a.service_code, a.action, a.entity_type, a.entity_id,
				       a.request_id
				from msal.audit_log a left join msal.app_users u on u.id = a.user_id
				""" + where + " order by a.id desc limit ? offset ?",
				(rs, i) -> new AuditEntry(rs.getLong("id"), rs.getTimestamp("occurred_at").toInstant(),
						rs.getObject("user_id", UUID.class), rs.getString("email"), rs.getString("service_code"),
						rs.getString("action"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
						rs.getString("request_id")),
				pageArgs.toArray());
		return new AuditPage(items, pageIndex, pageSize, total == null ? 0 : total);
	}

	// ── Helpers ─────────────────────────────────────────────────

	private Set<String> knownRoles() {
		return new HashSet<>(jdbc.queryForList("select code from msal.roles", String.class));
	}

	/** A PENDING invitation past its date shows as EXPIRED. */
	private static InvitationResponse toResponse(Invitation i, Instant now) {
		InvitationStatus status = i.getStatus() == InvitationStatus.PENDING && i.isExpired(now) ? InvitationStatus.EXPIRED
				: i.getStatus();
		return new InvitationResponse(i.getId(), i.getEmail(), i.getRoleCode(), status, i.getInvitedBy(),
				i.getExpiresAt(), i.getAcceptedAt(), i.getCreatedAt());
	}

	private static Map<String, Object> snapshot(AppUser user) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("status", user.getStatus());
		data.put("roles", user.getRoles().stream().sorted().toList());
		return data;
	}
}
