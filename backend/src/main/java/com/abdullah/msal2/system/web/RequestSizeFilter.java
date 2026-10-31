package com.abdullah.msal2.system.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.abdullah.msal2.system.error.ApiErrorWriter;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * Refuses API bodies over 1MB (API.md section 7) before they are read, with {@code SYS-E006}.
 * Imports (5MB) get their own limit in phase 6.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeFilter extends OncePerRequestFilter {

	static final long MAX_BYTES = 1024 * 1024;

	private final ApiErrorWriter errors;

	public RequestSizeFilter(ApiErrorWriter errors) {
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getRequestURI().startsWith("/api/") && request.getContentLengthLong() > MAX_BYTES) {
			errors.write(response, ErrorCode.SYS_E006);
			return;
		}
		chain.doFilter(request, response);
	}
}
