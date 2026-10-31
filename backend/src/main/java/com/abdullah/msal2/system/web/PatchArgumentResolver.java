package com.abdullah.msal2.system.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.abdullah.msal2.system.error.ApiError;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.error.InvalidRequestException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a {@link Patch} parameter from the request body: the JSON object, its field names,
 * and the DTO, validated with Bean Validation.
 */
public class PatchArgumentResolver implements HandlerMethodArgumentResolver {

	private final JsonMapper jsonMapper;
	private final Validator validator;

	public PatchArgumentResolver(JsonMapper jsonMapper, Validator validator) {
		this.jsonMapper = jsonMapper;
		this.validator = validator;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == Patch.class;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws IOException {
		Class<?> type = ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve();
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		JsonNode body;
		Object value;
		try (InputStream in = request.getInputStream()) {
			body = jsonMapper.readTree(in);
			if (body == null || !body.isObject()) {
				throw new ApiException(ErrorCode.SYS_E006);
			}
			value = jsonMapper.treeToValue(body, type);
		}
		catch (JacksonException e) {
			throw new ApiException(ErrorCode.SYS_E006);
		}

		Set<ConstraintViolation<Object>> violations = validator.validate(value);
		if (!violations.isEmpty()) {
			List<ApiError.Detail> details = violations.stream()
					.sorted((a, b) -> a.getPropertyPath().toString().compareTo(b.getPropertyPath().toString()))
					.map(v -> {
						ErrorCode code = ErrorCode.fromCode(v.getMessage()).orElse(ErrorCode.SYS_E006);
						return new ApiError.Detail(v.getPropertyPath().toString(), code.code(), code.message());
					})
					.toList();
			throw new InvalidRequestException(details);
		}
		return new Patch<>(value, new LinkedHashSet<>(body.propertyNames()));
	}
}
