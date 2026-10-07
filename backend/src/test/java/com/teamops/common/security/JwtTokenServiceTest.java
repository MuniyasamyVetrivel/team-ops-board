package com.teamops.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.teamops.support.TestFixtures;

class JwtTokenServiceTest {

	private final SecurityProperties properties = TestFixtures.securityProperties();

	private final JwtConfig config = new JwtConfig();

	private final SecretKey key = config.jwtSigningKey(properties);

	private final JwtDecoder decoder = config.jwtDecoder(key, properties);

	private final JwtTokenService service = new JwtTokenService(config.jwtEncoder(key), properties,
			Clock.systemUTC());

	private final AuthenticatedUser user = new AuthenticatedUser(42L, "rakesh@teamops.local", "Rakesh", 1L,
			Set.of("SUPER_ADMIN"), Set.of("TASK_VIEW"));

	@Test
	void issuedTokenRoundTripsWithSubjectIssuerAndExpiry() {
		AccessToken token = service.issue(user);

		Jwt jwt = decoder.decode(token.value());
		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("team-ops-board");
		assertThat(jwt.getClaimAsString("email")).isEqualTo("rakesh@teamops.local");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SUPER_ADMIN");
		assertThat(token.expiresInSeconds()).isEqualTo(Duration.ofMinutes(60).toSeconds());
		assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		SecretKey otherKey = JwtConfig.signingKey("another-secret-that-is-also-longer-than-32-bytes!!");
		JwtTokenService otherService = new JwtTokenService(config.jwtEncoder(otherKey), properties, Clock.systemUTC());

		String forged = otherService.issue(user).value();

		assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
	}

	@Test
	void expiredTokenIsRejected() {
		Clock twoHoursAgo = Clock.offset(Clock.systemUTC(), Duration.ofHours(-2));
		String expired = new JwtTokenService(config.jwtEncoder(key), properties, twoHoursAgo).issue(user).value();

		assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
	}

	@Test
	void shortSecretFailsFast() {
		assertThatThrownBy(() -> JwtConfig.signingKey("too-short")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 32 bytes");
	}

}
