package com.abdullah.msal2.system.error;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

/**
 * Error codes and their Arabic messages (API.md section 3).
 * <p>
 * {@code SYS-Eyyy} are general errors; {@code SRV-xxx-Eyyy} belong to one service.
 * Service codes are added here with their service.
 */
public enum ErrorCode {

	SYS_E001("SYS-E001", HttpStatus.UNAUTHORIZED, "انتهت الجلسة، سجّل الدخول مرة أخرى."),
	SYS_E002("SYS-E002", HttpStatus.FORBIDDEN, "ليست لديك صلاحية لهذه العملية."),
	SYS_E003("SYS-E003", HttpStatus.FORBIDDEN, "هذه الخدمة متوقفة مؤقتًا."),
	SYS_E004("SYS-E004", HttpStatus.NOT_FOUND, "العنصر غير موجود."),
	SYS_E005("SYS-E005", HttpStatus.CONFLICT, "تم تعديل هذا العنصر من جهاز آخر. حدّث الصفحة."),
	SYS_E006("SYS-E006", HttpStatus.BAD_REQUEST, "البيانات غير صحيحة."),
	SYS_E007("SYS-E007", HttpStatus.TOO_MANY_REQUESTS, "طلبات كثيرة، حاول بعد قليل."),
	SYS_E008("SYS-E008", HttpStatus.INTERNAL_SERVER_ERROR, "حدث خطأ غير متوقع."),
	SYS_E009("SYS-E009", HttpStatus.FORBIDDEN, "تم إيقاف هذا الحساب."),

	SRV_000_E001("SRV-000-E001", HttpStatus.FORBIDDEN, "هذا البريد غير مدعو لاستخدام التطبيق."),
	SRV_000_E002("SRV-000-E002", HttpStatus.BAD_REQUEST, "المنطقة الزمنية غير صحيحة.");

	private static final Map<String, ErrorCode> BY_CODE = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(ErrorCode::code, Function.identity()));

	private final String code;
	private final HttpStatus status;
	private final String message;

	ErrorCode(String code, HttpStatus status, String message) {
		this.code = code;
		this.status = status;
		this.message = message;
	}

	public String code() {
		return code;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}

	/**
	 * Finds a code by its text. Bean Validation messages can hold a code
	 * ({@code @Pattern(message = "SRV-001-E001")}), and the error handler maps it here.
	 */
	public static Optional<ErrorCode> fromCode(String code) {
		return Optional.ofNullable(code).map(BY_CODE::get);
	}
}
