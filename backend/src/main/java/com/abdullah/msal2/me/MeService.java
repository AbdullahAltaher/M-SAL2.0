package com.abdullah.msal2.me;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.push.WebPush;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.access.PermissionRepository;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.time.Today;
import com.abdullah.msal2.system.web.Patch;
import com.abdullah.msal2.users.AppUser;
import com.abdullah.msal2.users.AppUserRepository;

@Service
public class MeService {

	private final AppUserRepository users;
	private final PermissionRepository permissions;
	private final MeRepository me;
	private final AuditService audit;
	private final WebPush push;
	private final Clock clock;

	public MeService(AppUserRepository users, PermissionRepository permissions, MeRepository me, AuditService audit,
			WebPush push, Clock clock) {
		this.push = push;
		this.users = users;
		this.permissions = permissions;
		this.me = me;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public MeResponse get(CurrentUser current) {
		return toResponse(load(current));
	}

	@Transactional
	public MeResponse update(CurrentUser current, Patch<UpdateMeRequest> patch) {
		UpdateMeRequest request = patch.value();
		AppUser user = load(current);
		if (!Objects.equals(user.getVersion(), request.version())) {
			throw new ApiException(ErrorCode.SYS_E005);
		}
		Map<String, Object> before = snapshot(user);

		if (request.displayName() != null) {
			String name = request.displayName().trim();
			user.setDisplayName(name.isEmpty() ? null : name);
		}
		if (request.timeZone() != null) {
			if (!ZoneId.getAvailableZoneIds().contains(request.timeZone())) {
				throw new ApiException(ErrorCode.SRV_000_E002);
			}
			user.setTimeZone(request.timeZone());
		}
		if (request.lastBudgetId() != null) {
			// Another user's budget is "not found", never "forbidden" (CLAUDE.md rule 6)
			if (!me.ownsBudget(user.getId(), request.lastBudgetId())) {
				throw new ApiException(ErrorCode.SYS_E004);
			}
			user.setLastBudgetId(request.lastBudgetId());
		}
		if (patch.has("dailyReminderTime")) {
			LocalTime time = request.dailyReminderTime();
			user.setDailyReminderTime(time == null ? null : time.truncatedTo(ChronoUnit.MINUTES));
		}

		Map<String, Object> after = snapshot(user);
		if (!after.equals(before)) {
			users.saveAndFlush(user);
			audit.record(user.getId(), AuditAction.UPDATE, "app_user", user.getId(), before, after);
		}
		return toResponse(user);
	}

	private AppUser load(CurrentUser current) {
		return users.findById(current.id()).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}

	private MeResponse toResponse(AppUser user) {
		Instant now = Instant.now(clock);
		LocalDate today = Today.of(now, user.getTimeZone());
		Map<String, MeResponse.Rights> services = new LinkedHashMap<>();
		permissions.permitted(user.getId())
				.forEach((code, access) -> services.put(code, new MeResponse.Rights(access.canRead(), access.canWrite())));
		ExportReminder reminder = ExportReminder.of(user.getLastExportAt(), user.getCreatedAt(), now,
				me.hasData(user.getId()));
		return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getTimeZone(), today,
				user.getRoles().stream().sorted().toList(), services, user.getLastBudgetId(), reminder,
				user.getDailyReminderTime(), push.publicKey(), user.getVersion());
	}

	private static Map<String, Object> snapshot(AppUser user) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("displayName", user.getDisplayName());
		data.put("timeZone", user.getTimeZone());
		data.put("lastBudgetId", user.getLastBudgetId());
		data.put("dailyReminderTime", user.getDailyReminderTime());
		return data;
	}
}
