package com.abdullah.msal2.system;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.abdullah.msal2.system.access.CurrentUserArgumentResolver;
import com.abdullah.msal2.system.access.ServiceAccessInterceptor;

/**
 * Every {@link ServiceCode} controller is served under {@code /api/v1} (API.md section 1)
 * and goes through the access interceptor.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	public static final String API_PREFIX = "/api/v1";

	private final ServiceAccessInterceptor accessInterceptor;

	public WebConfig(ServiceAccessInterceptor accessInterceptor) {
		this.accessInterceptor = accessInterceptor;
	}

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forAnnotation(ServiceCode.class));
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(accessInterceptor).addPathPatterns("/api/**");
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new CurrentUserArgumentResolver());
	}
}
