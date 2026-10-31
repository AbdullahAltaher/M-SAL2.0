package com.abdullah.msal2.system;

import org.slf4j.MDC;

/**
 * Per-request values kept in the logging MDC: the request id, the service code and the user.
 * <p>
 * {@link com.abdullah.msal2.system.logging.RequestLoggingFilter} sets and clears them, and the
 * access interceptor adds the service and user. Logs and {@code audit_log} read them from here.
 */
public final class RequestContext {

	public static final String REQUEST_ID = "requestId";
	public static final String SERVICE = "service";
	public static final String USER = "user";

	private RequestContext() {
	}

	public static String requestId() {
		return MDC.get(REQUEST_ID);
	}

	public static String serviceCode() {
		return MDC.get(SERVICE);
	}
}
