package com.abdullah.msal2.system.error;

import java.util.List;

/**
 * Invalid fields found outside Spring's own {@code @Valid} handling (such as in a
 * {@link com.abdullah.msal2.system.web.Patch} body). The first field's code is the response code.
 */
public class InvalidRequestException extends RuntimeException {

	private final List<ApiError.Detail> details;

	public InvalidRequestException(List<ApiError.Detail> details) {
		super("Invalid request");
		this.details = List.copyOf(details);
	}

	public List<ApiError.Detail> details() {
		return details;
	}
}
