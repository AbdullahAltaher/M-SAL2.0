package com.abdullah.msal2.system.web;

import java.util.Set;

/**
 * A {@code PATCH} body that knows which fields were sent: a missing field stays unchanged,
 * and a field sent as {@code null} clears the value (such as a budget item's amount or the
 * next salary date). The body is validated like a {@code @Valid @RequestBody}.
 *
 * @param value  the body as its DTO
 * @param fields the JSON field names present in the body
 */
public record Patch<T>(T value, Set<String> fields) {

	public boolean has(String field) {
		return fields.contains(field);
	}
}
