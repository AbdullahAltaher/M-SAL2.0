package com.abdullah.msal2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Msal2Application {

	public static void main(String[] args) {
		SpringApplication.run(Msal2Application.class, args);
	}

}
