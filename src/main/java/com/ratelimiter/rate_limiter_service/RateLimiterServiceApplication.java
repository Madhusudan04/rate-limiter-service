package com.ratelimiter.rate_limiter_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RateLimiterServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RateLimiterServiceApplication.class, args);
	}

}
