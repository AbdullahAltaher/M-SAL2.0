package com.abdullah.msal2.system.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.abdullah.msal2.system.RequestContext;

/**
 * Gives every request an id ({@code X-Request-Id}, API.md section 1) and writes one log line
 * per API call (PLAN.md section 11):
 * <pre>[SRV-001] [user=3f2a...] POST /api/v1/entries → 201 (45ms)</pre>
 * Runs before Spring Security, so errors from the security filters carry the id too.
 * Amounts, descriptions and tokens are never logged (API.md section 7).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	private static final Logger log = LoggerFactory.getLogger("msal2.access");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		MDC.put(RequestContext.REQUEST_ID, requestId);
		response.setHeader(HEADER, requestId);
		long start = System.nanoTime();
		try {
			chain.doFilter(request, response);
		}
		finally {
			if (request.getRequestURI().startsWith("/api/")) {
				long ms = (System.nanoTime() - start) / 1_000_000;
				log.info("{} {} → {} ({}ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), ms);
			}
			MDC.remove(RequestContext.REQUEST_ID);
			MDC.remove(RequestContext.SERVICE);
			MDC.remove(RequestContext.USER);
		}
	}
}
