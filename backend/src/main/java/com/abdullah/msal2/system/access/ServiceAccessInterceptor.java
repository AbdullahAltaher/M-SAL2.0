package com.abdullah.msal2.system.access;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.abdullah.msal2.system.RequestContext;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.PermissionRepository.ServiceAccess;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * The permission check on every API request, in the order of API.md section 1:
 * <ol>
 * <li>the JWT is valid (already checked by Spring Security, {@code SYS-E001});</li>
 * <li>the user exists or may be created ({@code SRV-000-E001}), and is not disabled ({@code SYS-E009});</li>
 * <li>the controller's service is enabled ({@code SYS-E003});</li>
 * <li>{@code GET} needs read, every other method needs write ({@code SYS-E002});</li>
 * <li>the user is within the request limit ({@code SYS-E007}, API.md section 7).</li>
 * </ol>
 * A controller without {@link ServiceCode} is refused: access fails closed.
 */
@Component
public class ServiceAccessInterceptor implements HandlerInterceptor {

	static final String CURRENT_USER = ServiceAccessInterceptor.class.getName() + ".currentUser";

	private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");

	private final CurrentUserResolver users;
	private final PermissionRepository permissions;
	private final RateLimiter rateLimiter;

	public ServiceAccessInterceptor(CurrentUserResolver users, PermissionRepository permissions,
			RateLimiter rateLimiter) {
		this.users = users;
		this.permissions = permissions;
		this.rateLimiter = rateLimiter;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		ServiceCode serviceCode = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), ServiceCode.class);
		if (serviceCode == null) {
			throw new IllegalStateException("Controller without @ServiceCode: " + method.getBeanType().getName());
		}
		String code = serviceCode.value();
		MDC.put(RequestContext.SERVICE, code);

		Jwt jwt = currentJwt();
		MDC.put(RequestContext.USER, jwt.getSubject());
		CurrentUser user = users.resolve(jwt);
		if (user.disabled()) {
			throw new ApiException(ErrorCode.SYS_E009);
		}

		ServiceAccess access = permissions.access(user.id(), code)
				.orElseThrow(() -> new IllegalStateException("Unknown service code: " + code));
		if (!access.enabled()) {
			throw new ApiException(ErrorCode.SYS_E003);
		}
		boolean allowed = READ_METHODS.contains(request.getMethod()) ? access.canRead() : access.canWrite();
		if (!allowed) {
			throw new ApiException(ErrorCode.SYS_E002);
		}
		if (!rateLimiter.tryAcquire(user.id(), method.hasMethodAnnotation(StrictRateLimit.class))) {
			throw new ApiException(ErrorCode.SYS_E007);
		}

		request.setAttribute(CURRENT_USER, user);
		return true;
	}

	private static Jwt currentJwt() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
			return jwt;
		}
		throw new ApiException(ErrorCode.SYS_E001);
	}
}
