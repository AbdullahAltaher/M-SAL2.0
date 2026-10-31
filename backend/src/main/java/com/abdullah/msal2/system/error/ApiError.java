package com.abdullah.msal2.system.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error response body (API.md 3.1).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<Detail> details, String requestId, Instant timestamp) {

	public record Detail(String field, String code, String message) {
	}

	public static ApiError of(ErrorCode errorCode, List<Detail> details, String requestId, Instant timestamp) {
		return new ApiError(errorCode.code(), errorCode.message(), details, requestId, timestamp);
	}
}
