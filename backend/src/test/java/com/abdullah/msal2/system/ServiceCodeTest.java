package com.abdullah.msal2.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.IntegrationTest;

import tools.jackson.databind.json.JsonMapper;

/**
 * App-wide rules from CLAUDE.md that are easy to break by accident.
 */
class ServiceCodeTest extends IntegrationTest {

	@Autowired
	private JsonMapper jsonMapper;

	/** Rule 5: every controller carries an existing service code. */
	@Test
	void everyControllerHasAKnownServiceCode() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
		Set<BeanDefinition> controllers = scanner.findCandidateComponents("com.abdullah.msal2");
		List<String> known = jdbc.queryForList("select code from msal.services", String.class);

		assertThat(controllers).isNotEmpty();
		for (BeanDefinition definition : controllers) {
			Class<?> type = ClassUtils.resolveClassName(definition.getBeanClassName(), null);
			ServiceCode code = type.getAnnotation(ServiceCode.class);
			assertThat(code).as("@ServiceCode on %s", type.getSimpleName()).isNotNull();
			assertThat(known).as("service code of %s", type.getSimpleName()).contains(code.value());
		}
	}

	/** Rule 6: amounts are strings in JSON. */
	@Test
	void amountsAreWrittenAsStrings() {
		assertThat(jsonMapper.writeValueAsString(Map.of("amount", new BigDecimal("46.25"))))
				.isEqualTo("{\"amount\":\"46.25\"}");
	}
}
