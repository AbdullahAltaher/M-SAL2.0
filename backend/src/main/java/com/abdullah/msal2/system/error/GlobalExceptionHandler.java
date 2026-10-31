package com.abdullah.msal2.system.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every error into the response shape in API.md 3.1 (API.md section 8, note 2).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ApiErrorWriter errors;

	public GlobalExceptionHandler(ApiErrorWriter errors) {
		this.errors = errors;
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApi(ApiException e) {
		return errors.response(e.errorCode(), List.of());
	}

	/**
	 * Bean Validation errors. A constraint message that is an error code (such as
	 * {@code "SRV-001-E001"}) gives that code; anything else is {@code SYS-E006}.
	 * The first field's code becomes the response code.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleInvalid(MethodArgumentNotValidException e) {
		List<ApiError.Detail> details = e.getBindingResult().getFieldErrors().stream()
				.map(GlobalExceptionHandler::detail)
				.toList();
		ErrorCode top = details.isEmpty() ? ErrorCode.SYS_E006
				: ErrorCode.fromCode(details.getFirst().code()).orElse(ErrorCode.SYS_E006);
		return errors.response(top, details);
	}

	private static ApiError.Detail detail(FieldError fieldError) {
		ErrorCode code = ErrorCode.fromCode(fieldError.getDefaultMessage()).orElse(ErrorCode.SYS_E006);
		return new ApiError.Detail(fieldError.getField(), code.code(), code.message());
	}

	@ExceptionHandler(InvalidRequestException.class)
	ResponseEntity<ApiError> handleInvalidPatch(InvalidRequestException e) {
		ErrorCode top = ErrorCode.fromCode(e.details().getFirst().code()).orElse(ErrorCode.SYS_E006);
		return errors.response(top, e.details());
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, HandlerMethodValidationException.class,
			MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
			MissingServletRequestPartException.class })
	ResponseEntity<ApiError> handleBadRequest(Exception e) {
		return errors.response(ErrorCode.SYS_E006, List.of());
	}

	/** An import file over 5MB sent without a Content-Length (RequestSizeFilter catches the rest). */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
		return errors.response(ErrorCode.SRV_007_E003, List.of());
	}

	@ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class })
	ResponseEntity<ApiError> handleNotFound(Exception e) {
		return errors.response(ErrorCode.SYS_E004, List.of());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<ApiError> handleConflict(ObjectOptimisticLockingFailureException e) {
		return errors.response(ErrorCode.SYS_E005, List.of());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiError> handleDenied(AccessDeniedException e) {
		return errors.response(ErrorCode.SYS_E002, List.of());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception e) {
		log.error("Unexpected error", e);
		return errors.response(ErrorCode.SYS_E008, List.of());
	}
}
