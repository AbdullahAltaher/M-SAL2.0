package com.abdullah.msal2.system;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The service a controller belongs to, such as {@code "SRV-001"} (CLAUDE.md rule 5).
 * <p>
 * Every controller must carry it. It drives the permission check
 * ({@link com.abdullah.msal2.system.access.ServiceAccessInterceptor}), the log context (MDC)
 * and {@code audit_log.service_code}. Annotated controllers are served under {@code /api/v1}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceCode {

	String value();
}
