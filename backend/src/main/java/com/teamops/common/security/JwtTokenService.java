package com.teamops.common.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Issues short-lived access tokens. Claims other than the subject are informational only: authorities are
 * re-resolved from the database on every request (see {@link DatabaseJwtAuthenticationConverter}).
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

	private final JwtEncoder jwtEncoder;

	private final SecurityProperties properties;

	private final Clock clock;

	public AccessToken issue(AuthenticatedUser user) {
		// JWT timestamps are whole seconds; truncate so the reported expiry matches the token exactly.
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.jwt().issuer())
			.subject(String.valueOf(user.id()))
			.id(UUID.randomUUID().toString())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.claim("email", user.email())
			.claim("roles", List.copyOf(user.roles()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(token, expiresAt, properties.jwt().accessTokenTtl().toSeconds());
	}

}
