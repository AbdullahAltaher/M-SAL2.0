package com.abdullah.msal2.users;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.abdullah.msal2.budget.Budget;
import com.abdullah.msal2.budget.BudgetService;
import com.abdullah.msal2.system.MsalProperties;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.access.CurrentUserResolver;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.time.Today;

/**
 * Finds the user of a request, and creates it on the first sign-in (API.md 2.4):
 * <ul>
 * <li>a pending, unexpired invitation for the email: the user gets {@code USER}, plus
 * {@code ADMIN} if the invitation is for an admin, and the invitation becomes ACCEPTED;</li>
 * <li>the bootstrap admin email while no user exists yet: {@code USER} and {@code ADMIN} (API.md 2.5);</li>
 * <li>anyone else: {@code SRV-000-E001}.</li>
 * </ul>
 */
@Service
public class UserProvisioningService implements CurrentUserResolver {

	private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

	private final AppUserRepository users;
	private final InvitationRepository invitations;
	private final BudgetService budgets;
	private final AuditService audit;
	private final MsalProperties properties;
	private final TransactionTemplate tx;
	private final Clock clock;

	public UserProvisioningService(AppUserRepository users, InvitationRepository invitations, BudgetService budgets,
			AuditService audit,
			MsalProperties properties, TransactionTemplate tx, Clock clock) {
		this.users = users;
		this.invitations = invitations;
		this.budgets = budgets;
		this.audit = audit;
		this.properties = properties;
		this.tx = tx;
		this.clock = clock;
	}

	@Override
	public CurrentUser resolve(Jwt jwt) {
		UUID id = UUID.fromString(jwt.getSubject());
		Optional<AppUser> existing = users.findById(id);
		if (existing.isPresent()) {
			return toCurrentUser(existing.get());
		}
		try {
			return tx.execute(status -> toCurrentUser(provision(id, jwt.getClaimAsString("email"))));
		}
		catch (DataIntegrityViolationException e) {
			// Two first requests at the same time: the other one created the user
			return users.findById(id).map(UserProvisioningService::toCurrentUser).orElseThrow(() -> e);
		}
	}

	private AppUser provision(UUID id, String rawEmail) {
		if (rawEmail == null || rawEmail.isBlank()) {
			throw new ApiException(ErrorCode.SRV_000_E001);
		}
		String email = rawEmail.trim().toLowerCase(Locale.ROOT);
		Instant now = Instant.now(clock);

		Set<String> roles = new LinkedHashSet<>();
		roles.add(Roles.USER);
		String via;
		Optional<Invitation> invitation = invitations
				.findFirstByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING)
				.filter(i -> !i.isExpired(now));
		if (invitation.isPresent()) {
			roles.add(invitation.get().getRoleCode());
			invitation.get().accept(now);
			via = "INVITATION";
		}
		else if (isBootstrapAdmin(email)) {
			roles.add(Roles.ADMIN);
			via = "BOOTSTRAP";
		}
		else {
			log.info("Sign-in refused: email is not invited");
			throw new ApiException(ErrorCode.SRV_000_E001);
		}

		AppUser user = users.saveAndFlush(new AppUser(id, email, roles));
		// Every new user starts with the budget "الأساسي" (API.md 2.4, R-BUD-06)
		Budget first = budgets.createWithDefaults(id, BudgetService.FIRST_BUDGET_NAME, Today.of(now, user.getTimeZone()));
		user.setLastBudgetId(first.getId());
		users.saveAndFlush(user);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("email", email);
		after.put("roles", roles);
		after.put("via", via);
		audit.record(id, AuditAction.CREATE, "app_user", id, null, after);
		log.info("User created ({})", via);
		return user;
	}

	private boolean isBootstrapAdmin(String email) {
		String admin = properties.bootstrapAdminEmail();
		return admin != null && !admin.isBlank() && admin.trim().equalsIgnoreCase(email) && users.count() == 0;
	}

	private static CurrentUser toCurrentUser(AppUser user) {
		return new CurrentUser(user.getId(), user.getEmail(), user.getTimeZone(),
				user.getStatus() == UserStatus.DISABLED, user.getRoles());
	}
}
