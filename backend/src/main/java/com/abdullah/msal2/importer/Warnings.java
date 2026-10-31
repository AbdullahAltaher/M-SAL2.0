package com.abdullah.msal2.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an import fixes or skips (DATABASE.md 6.2), counted by kind, with Arabic messages for the
 * preview (API.md SRV-007) and {@code imports.warnings}.
 */
public final class Warnings {

	public enum Code {
		CATEGORY_MISSING("مصاريف بفئة غير موجودة، ستُربط بفئة «أخرى»"),
		ACCOUNT_MISSING("مصاريف بحساب غير موجود، ستُربط بأول حساب"),
		DATE_INVALID("مصاريف بتاريخ غير صالح، سيُستخدم تاريخ إنشائها"),
		AMOUNT_INVALID("مصاريف بمبلغ غير صالح أو صفر، لن تُستورد (تبقى محفوظة في الملف)"),
		CYCLE_INVALID("دورات بتاريخ غير صالح أو مكرر، لن تُستورد"),
		AMOUNT_TOO_LARGE("مبالغ في الميزانية أكبر من المسموح، ستُترك فارغة");

		private final String message;

		Code(String message) {
			this.message = message;
		}
	}

	public record Warning(String code, int count, String message) {
	}

	private final Map<Code, Integer> counts = new LinkedHashMap<>();

	void add(Code code) {
		counts.merge(code, 1, Integer::sum);
	}

	/** "مصاريف بفئة غير موجودة، ستُربط بفئة «أخرى» (2)" */
	public List<Warning> list() {
		List<Warning> list = new ArrayList<>();
		counts.forEach((code, n) -> list.add(new Warning(code.name(), n, code.message + " (" + n + ")")));
		return list;
	}
}
