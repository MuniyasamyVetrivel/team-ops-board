package com.teamops.auth.service;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.auth.dto.LoginRequest;
import com.teamops.auth.dto.MeResponse;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessToken;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.AuthenticatedUserFactory;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.web.ClientInfo;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

@Service
public class AuthService {

	static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

	static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final AuthenticatedUserFactory authenticatedUserFactory;

	private final JwtTokenService jwtTokenService;

	private final RefreshTokenService refreshTokenService;

	private final AuditService auditService;

	private final Clock clock;

	/** Compared against when the email is unknown, so response time does not reveal which emails exist. */
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticatedUserFactory authenticatedUserFactory, JwtTokenService jwtTokenService,
			RefreshTokenService refreshTokenService, AuditService auditService, Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticatedUserFactory = authenticatedUserFactory;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokenService = refreshTokenService;
		this.auditService = auditService;
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
	}

	@Transactional(noRollbackFor = ApiException.class)
	public AuthResult login(LoginRequest request, ClientInfo client) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		Optional<User> found = userRepository.findWithAuthoritiesByEmailIgnoreCase(email);
		boolean passwordMatches = passwordEncoder.matches(request.password(),
				found.map(User::getPasswordHash).orElse(dummyPasswordHash));

		if (found.isEmpty() || !passwordMatches) {
			Long userId = found.map(User::getId).orElse(null);
			auditService.record(AuditAction.LOGIN_FAILED, userId, "USER", userId,
					Map.of("email", email, "reason", found.isEmpty() ? "UNKNOWN_EMAIL" : "BAD_PASSWORD"), client);
			throw ApiException.unauthorized(INVALID_CREDENTIALS, "Invalid email or password");
		}

		User user = found.get();
		if (!user.isActive()) {
			auditService.record(AuditAction.LOGIN_FAILED, user.getId(), "USER", user.getId(),
					Map.of("email", email, "reason", ACCOUNT_DISABLED), client);
			throw ApiException.forbidden(ACCOUNT_DISABLED,
					"Your account has been disabled. Please contact your administrator.");
		}

		user.setLastLoginAt(clock.instant());
		auditService.record(AuditAction.LOGIN, user.getId(), "USER", user.getId(), Map.of(), client);
		return startSession(user, client);
	}

	@Transactional(noRollbackFor = ApiException.class)
	public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
		if (!StringUtils.hasText(rawRefreshToken)) {
			throw ApiException.unauthorized("SESSION_EXPIRED", "Your session has expired. Please sign in again.");
		}
		Long userId = refreshTokenService.consume(rawRefreshToken, client).getId();
		User user = userRepository.findWithAuthoritiesById(userId)
			.filter(User::isActive)
			.orElseThrow(() -> ApiException.unauthorized("SESSION_EXPIRED",
					"Your session has expired. Please sign in again."));
		return startSession(user, client);
	}

	@Transactional
	public void logout(String rawRefreshToken, ClientInfo client) {
		if (!StringUtils.hasText(rawRefreshToken)) {
			return;
		}
		refreshTokenService.revoke(rawRefreshToken)
			.ifPresent(userId -> auditService.record(AuditAction.LOGOUT, userId, "USER", userId, Map.of(), client));
	}

	@Transactional(readOnly = true)
	public MeResponse me(Long userId) {
		User user = userRepository.findWithAuthoritiesById(userId)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
		return MeResponse.of(user, authenticatedUserFactory.from(user));
	}

	private AuthResult startSession(User user, ClientInfo client) {
		AuthenticatedUser principal = authenticatedUserFactory.from(user);
		AccessToken accessToken = jwtTokenService.issue(principal);
		IssuedRefreshToken refreshToken = refreshTokenService.issue(user, client);
		return new AuthResult(accessToken, refreshToken, MeResponse.of(user, principal));
	}

}
