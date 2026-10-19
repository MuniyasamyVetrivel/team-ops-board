package com.teamops.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * application-prod.yml as the Docker Compose deployment expects it (docs/deployment.md). The proxy regex must accept
 * Nginx on the "app" network (172.28.1.0/24 in docker-compose.prod.yml) and nothing else, or audit logs would record
 * Nginx's address, or accept a forged X-Forwarded-For.
 */
class ProdProfileTest {

	private static PropertySource<?> prod;

	@BeforeAll
	static void load() throws IOException {
		prod = new YamlPropertySourceLoader().load("prod", new ClassPathResource("application-prod.yml")).getFirst();
	}

	private static String value(String key) {
		Object value = prod.getProperty(key);
		return value == null ? null : value.toString();
	}

	@Test
	void locksDownWhatProductionMustNotLoosen() {
		assertThat(value("app.security.cors.allowed-origins")).isEqualTo("https://ops.tritusa.com.au");
		assertThat(value("app.security.refresh-cookie.secure")).isEqualTo("true");
		assertThat(value("app.security.refresh-cookie.same-site")).isEqualTo("Strict");
		assertThat(value("management.endpoints.web.exposure.include")).isEqualTo("health");
		assertThat(value("management.endpoint.health.show-details")).isEqualTo("never");
		assertThat(value("app.dev-seed.enabled")).isEqualTo("false");
		assertThat(value("spring.flyway.enabled")).isEqualTo("true");
		assertThat(value("spring.flyway.clean-disabled")).isEqualTo("true");
		assertThat(value("app.time-zone")).isEqualTo("Asia/Kolkata");
		assertThat(value("logging.file.name")).isEmpty();
	}

	@Test
	void trustsForwardedHeadersFromTheNginxNetworkOnly() {
		assertThat(value("server.forward-headers-strategy")).isEqualTo("native");
		Pattern proxies = Pattern.compile(value("server.tomcat.remoteip.internal-proxies"));
		assertThat(proxies.matcher("172.28.1.2").matches()).isTrue();
		assertThat(proxies.matcher("172.28.1.254").matches()).isTrue();
		// cloudflared's network, the database network, the host and anything else are not trusted.
		for (String other : new String[] { "172.28.0.5", "172.28.2.3", "127.0.0.1", "10.0.0.1", "172.28.10.1",
				"172.28.1.1234", "203.0.113.9" }) {
			assertThat(proxies.matcher(other).matches()).as(other).isFalse();
		}
	}

}
