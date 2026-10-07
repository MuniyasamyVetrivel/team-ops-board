package com.teamops.devdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.dev-seed.*} (DEV_SEED_ENABLED / DEV_SEED_PASSWORD). Never enable in production. */
@ConfigurationProperties(prefix = "app.dev-seed")
public record DevSeedProperties(boolean enabled, String password) {

	@Override
	public String toString() {
		return "DevSeedProperties[enabled=" + enabled + "]";
	}

}
