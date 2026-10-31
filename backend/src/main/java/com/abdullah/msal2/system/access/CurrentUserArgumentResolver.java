package com.abdullah.msal2.system.access;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Passes the {@link CurrentUser} set by {@link ServiceAccessInterceptor} to controller methods.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == CurrentUser.class;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Object user = webRequest.getAttribute(ServiceAccessInterceptor.CURRENT_USER, RequestAttributes.SCOPE_REQUEST);
		if (user == null) {
			throw new IllegalStateException("No current user: the access interceptor did not run");
		}
		return user;
	}
}
