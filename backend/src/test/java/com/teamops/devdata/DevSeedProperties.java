package com.teamops.devdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.dev-seed.*}: switches on the test-only demo seeder. */
@ConfigurationProperties(prefix = "app.dev-seed")
public record DevSeedProperties(boolean enabled, String password) {

	@Override
	public String toString() {
		return "DevSeedProperties[enabled=" + enabled + "]";
	}

}
