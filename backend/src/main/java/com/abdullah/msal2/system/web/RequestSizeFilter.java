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
 * Imports may send a 5MB file (SRV-007-E003), plus room for the multipart wrapping.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeFilter extends OncePerRequestFilter {

	static final long MAX_BYTES = 1024 * 1024;

	/** The import file limit; the Spring multipart limit (application.yml) is the same. */
	public static final long MAX_IMPORT_BYTES = 5 * 1024 * 1024;
	private static final long MULTIPART_ROOM = 64 * 1024;

	private final ApiErrorWriter errors;

	public RequestSizeFilter(ApiErrorWriter errors) {
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String uri = request.getRequestURI();
		long length = request.getContentLengthLong();
		if (uri.startsWith("/api/v1/imports")) {
			if (length > MAX_IMPORT_BYTES + MULTIPART_ROOM) {
				errors.write(response, ErrorCode.SRV_007_E003);
				return;
			}
		}
		else if (uri.startsWith("/api/") && length > MAX_BYTES) {
			errors.write(response, ErrorCode.SYS_E006);
			return;
		}
		chain.doFilter(request, response);
	}
}
