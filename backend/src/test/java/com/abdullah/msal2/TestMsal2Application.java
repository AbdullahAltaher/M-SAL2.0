package com.abdullah.msal2;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app against a Testcontainers Postgres: {@code ./mvnw spring-boot:test-run}.
 */
public class TestMsal2Application {

	public static void main(String[] args) {
		SpringApplication.from(Msal2Application::main).with(TestcontainersConfiguration.class).run(args);
	}
}
