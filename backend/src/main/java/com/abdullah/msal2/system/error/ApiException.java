package com.abdullah.msal2.system.error;

/**
 * A business error with its code. The error handler turns it into the response in API.md 3.1.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;

	public ApiException(ErrorCode errorCode) {
		super(errorCode.code());
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}
}
