package com.teamops.common.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** HS256 JWT signing/verification using Spring Security's Nimbus support (no third-party JWT library). */
@Configuration
public class JwtConfig {

	static final int MIN_SECRET_BYTES = 32;

	@Bean
	public SecretKey jwtSigningKey(SecurityProperties properties) {
		return signingKey(properties.jwt().secret());
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
		return decoder;
	}

	static SecretKey signingKey(String secret) {
		byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES
					+ " bytes long. Generate one with: openssl rand -base64 48");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

}
