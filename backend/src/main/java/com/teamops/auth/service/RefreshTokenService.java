package com.teamops.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.auth.entity.RefreshToken;
import com.teamops.auth.repository.RefreshTokenRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.SecurityProperties;
import com.teamops.common.web.ClientInfo;
import com.teamops.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Rotating refresh tokens. Each token is single-use: consuming it revokes it and the caller issues a new one.
 * Presenting an already-revoked token (outside a short grace window for concurrent refreshes) is treated as token
 * theft and revokes every session of that user.
 * <p>
 * Runs inside the caller's transaction ({@link AuthService}), which must not roll back on {@link ApiException} so
 * that a theft-triggered revocation is kept.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
public class RefreshTokenService {

	static final Duration REUSE_GRACE_PERIOD = Duration.ofSeconds(10);

	private static final int TOKEN_BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;

	private final SecurityProperties securityProperties;

	private final AuditService auditService;

	private final Clock clock;

	public IssuedRefreshToken issue(User user, ClientInfo client) {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expiresAt = clock.instant().plus(securityProperties.jwt().refreshTokenTtl());

		RefreshToken token = new RefreshToken();
		token.setUser(user);
		token.setTokenHash(hash(raw));
		token.setExpiresAt(expiresAt);
		token.setIpAddress(client.ipAddress());
		token.setUserAgent(client.userAgent());
		refreshTokenRepository.save(token);
		return new IssuedRefreshToken(raw, expiresAt);
	}

	/** Validates and revokes the presented token, returning its owner. */
	public User consume(String rawToken, ClientInfo client) {
		Instant now = clock.instant();
		RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken)).orElseThrow(this::invalid);
		if (token.isRevoked()) {
			if (token.getRevokedAt().plus(REUSE_GRACE_PERIOD).isBefore(now)) {
				Long userId = token.getUser().getId();
				refreshTokenRepository.revokeAllActiveForUser(userId, now);
				auditService.record(AuditAction.REFRESH_TOKEN_REUSE, userId, "USER", userId,
						Map.of("refreshTokenId", token.getId()), client);
			}
			throw invalid();
		}
		if (token.isExpiredAt(now)) {
			throw invalid();
		}
		token.setRevokedAt(now);
		return token.getUser();
	}

	/** Revokes the token if it exists and is still active; returns its owner's id. */
	public Optional<Long> revoke(String rawToken) {
		Instant now = clock.instant();
		return refreshTokenRepository.findByTokenHash(hash(rawToken)).filter(token -> !token.isRevoked()).map(token -> {
			token.setRevokedAt(now);
			return token.getUser().getId();
		});
	}

	static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

	private ApiException invalid() {
		return ApiException.unauthorized("SESSION_EXPIRED", "Your session has expired. Please sign in again.");
	}

}
