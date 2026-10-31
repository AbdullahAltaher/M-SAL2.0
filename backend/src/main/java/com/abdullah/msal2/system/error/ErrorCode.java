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
	SRV_000_E002("SRV-000-E002", HttpStatus.BAD_REQUEST, "المنطقة الزمنية غير صحيحة."),

	SRV_001_E001("SRV-001-E001", HttpStatus.BAD_REQUEST, "أدخل مبلغًا صحيحًا أكبر من صفر."),
	SRV_001_E002("SRV-001-E002", HttpStatus.BAD_REQUEST, "اختر تصنيفًا."),
	SRV_001_E003("SRV-001-E003", HttpStatus.BAD_REQUEST, "اكتب وصف"),
	SRV_001_E004("SRV-001-E004", HttpStatus.BAD_REQUEST, "تاريخ غير صالح."),
	SRV_001_E005("SRV-001-E005", HttpStatus.BAD_REQUEST, "الحساب غير موجود."),
	SRV_001_E006("SRV-001-E006", HttpStatus.CONFLICT, "هذا العنصر ليس في المحذوفات."),

	SRV_002_E001("SRV-002-E001", HttpStatus.BAD_REQUEST, "أدخل اسمًا."),
	SRV_002_E002("SRV-002-E002", HttpStatus.CONFLICT, "ما يمكن حذف آخر شخص. أضف شخص ثاني أول."),
	SRV_002_E003("SRV-002-E003", HttpStatus.BAD_REQUEST, "المبلغ غير صحيح."),

	SRV_003_E001("SRV-003-E001", HttpStatus.BAD_REQUEST, "اختر تاريخًا صحيحًا."),
	SRV_003_E002("SRV-003-E002", HttpStatus.BAD_REQUEST, "أدخل مبلغًا صحيحًا للمخصَّص (يمكن أن يكون 0)."),
	SRV_003_E003("SRV-003-E003", HttpStatus.BAD_REQUEST, "تاريخ الراتب القادم يجب أن يكون بعد بداية الدورة."),
	SRV_003_E004("SRV-003-E004", HttpStatus.CONFLICT,
			"توجد دورة أخرى بنفس هذا التاريخ. اختر تاريخًا مختلفًا أو عدّل تلك الدورة."),

	SRV_004_E001("SRV-004-E001", HttpStatus.BAD_REQUEST, "أدخل اسم الحساب."),
	SRV_004_E002("SRV-004-E002", HttpStatus.CONFLICT, "لا يمكن أرشفة آخر حساب نشط."),

	SRV_005_E001("SRV-005-E001", HttpStatus.BAD_REQUEST, "أدخل اسم الفئة."),
	SRV_005_E002("SRV-005-E002", HttpStatus.BAD_REQUEST, "اختر رمزًا."),
	SRV_005_E003("SRV-005-E003", HttpStatus.BAD_REQUEST, "قائمة الترتيب لا تطابق الفئات الحالية."),

	SRV_007_E001("SRV-007-E001", HttpStatus.BAD_REQUEST, "الملف ليس JSON صالحًا."),
	SRV_007_E002("SRV-007-E002", HttpStatus.BAD_REQUEST, "صيغة الملف غير معروفة."),
	SRV_007_E003("SRV-007-E003", HttpStatus.CONTENT_TOO_LARGE, "الملف كبير جدًا (الحد 5 ميغابايت)."),

	SRV_009_E001("SRV-009-E001", HttpStatus.CONFLICT, "هذا البريد مستخدم بالفعل."),
	SRV_009_E002("SRV-009-E002", HttpStatus.CONFLICT, "توجد دعوة معلّقة لهذا البريد."),
	SRV_009_E003("SRV-009-E003", HttpStatus.CONFLICT, "لا يمكنك إيقاف حسابك أو إزالة صلاحية المدير عن نفسك."),
	SRV_009_E004("SRV-009-E004", HttpStatus.BAD_GATEWAY, "تعذّر إنشاء المستخدم في نظام الدخول.");

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
