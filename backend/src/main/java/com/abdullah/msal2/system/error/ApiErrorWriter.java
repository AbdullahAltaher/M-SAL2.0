package com.abdullah.msal2.system.error;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.abdullah.msal2.system.RequestContext;

import tools.jackson.databind.json.JsonMapper;

/**
 * Builds error responses in one shape, for controllers and for the security filters
 * that run before Spring MVC.
 */
@Component
public class ApiErrorWriter {

	private final Clock clock;
	private final JsonMapper jsonMapper;

	public ApiErrorWriter(Clock clock, JsonMapper jsonMapper) {
		this.clock = clock;
		this.jsonMapper = jsonMapper;
	}

	public ApiError body(ErrorCode errorCode, List<ApiError.Detail> details) {
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
		return ApiError.of(errorCode, details, RequestContext.requestId(), now);
	}

	public ResponseEntity<ApiError> response(ErrorCode errorCode, List<ApiError.Detail> details) {
		return ResponseEntity.status(errorCode.status()).body(body(errorCode, details));
	}

	/** Writes the error straight to the servlet response (used outside Spring MVC). */
	public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), body(errorCode, List.of()));
	}
}
