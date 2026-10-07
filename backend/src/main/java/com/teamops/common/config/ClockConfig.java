package com.teamops.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single UTC clock so time-dependent logic can be tested with a fixed clock. */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
