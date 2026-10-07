package com.teamops.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.teamops.auth.dto.LoginRequest;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessToken;
import com.teamops.common.security.AuthenticatedUserFactory;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.web.ClientInfo;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.PermissionRepository;
import com.teamops.user.repository.UserRepository;

class AuthServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private final UserRepository userRepository = mock(UserRepository.class);

	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

	private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);

	private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

	private final AuditService auditService = mock(AuditService.class);

	private final ClientInfo client = new ClientInfo("10.0.0.5", "JUnit");

	private AuthService authService;

	private User employee;

	@BeforeEach
	void setUp() {
		when(passwordEncoder.encode(any())).thenReturn("$2a$12$dummy");
		authService = new AuthService(userRepository, passwordEncoder,
				new AuthenticatedUserFactory(mock(PermissionRepository.class)), jwtTokenService, refreshTokenService,
				auditService, Clock.fixed(NOW, ZoneOffset.UTC));
		employee = TestFixtures.user(5L, "karthik.raj@teamops.local",
				TestFixtures.role(3L, "EMPLOYEE", "TASK_VIEW"));
		when(jwtTokenService.issue(any())).thenReturn(new AccessToken("jwt", NOW.plusSeconds(3600), 3600));
		when(refreshTokenService.issue(any(), any()))
			.thenReturn(new IssuedRefreshToken("refresh", NOW.plusSeconds(604800)));
	}

	@Test
	void loginWithValidCredentialsStartsSessionAndAudits() {
		when(userRepository.findWithAuthoritiesByEmailIgnoreCase("karthik.raj@teamops.local"))
			.thenReturn(Optional.of(employee));
		when(passwordEncoder.matches("secret-pass", "$2a$12$hash")).thenReturn(true);

		AuthResult result = authService.login(new LoginRequest("  Karthik.Raj@TeamOps.local ", "secret-pass"), client);

		assertThat(result.accessToken().value()).isEqualTo("jwt");
		assertThat(result.refreshToken().value()).isEqualTo("refresh");
		assertThat(result.user().email()).isEqualTo("karthik.raj@teamops.local");
		assertThat(result.user().permissions()).containsExactly("TASK_VIEW");
		assertThat(employee.getLastLoginAt()).isEqualTo(NOW);
		verify(auditService).record(eq(AuditAction.LOGIN), eq(5L), eq("USER"), eq(5L), anyMap(), eq(client));
	}

	@Test
	void loginWithWrongPasswordIsRejectedAndAudited() {
		when(userRepository.findWithAuthoritiesByEmailIgnoreCase(any())).thenReturn(Optional.of(employee));
		when(passwordEncoder.matches(any(), any())).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest("karthik.raj@teamops.local", "wrong"), client))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
				assertThat(ex.getCode()).isEqualTo(AuthService.INVALID_CREDENTIALS);
			});
		verify(auditService).record(eq(AuditAction.LOGIN_FAILED), eq(5L), eq("USER"), eq(5L), anyMap(), eq(client));
		verify(refreshTokenService, never()).issue(any(), any());
	}

	@Test
	void loginWithUnknownEmailStillRunsPasswordCheckAndGivesSameError() {
		when(userRepository.findWithAuthoritiesByEmailIgnoreCase(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@teamops.local", "whatever"), client))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.getCode()).isEqualTo(AuthService.INVALID_CREDENTIALS));
		verify(passwordEncoder).matches("whatever", "$2a$12$dummy");
		verify(auditService).record(eq(AuditAction.LOGIN_FAILED), isNull(), eq("USER"), isNull(), anyMap(),
				eq(client));
	}

	@Test
	void disabledUserWithCorrectPasswordIsForbidden() {
		employee.setStatus(UserStatus.DISABLED);
		when(userRepository.findWithAuthoritiesByEmailIgnoreCase(any())).thenReturn(Optional.of(employee));
		when(passwordEncoder.matches(any(), any())).thenReturn(true);

		assertThatThrownBy(() -> authService.login(new LoginRequest("karthik.raj@teamops.local", "pw"), client))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
				assertThat(ex.getCode()).isEqualTo(AuthService.ACCOUNT_DISABLED);
			});
		verify(refreshTokenService, never()).issue(any(), any());
	}

	@Test
	void refreshRotatesTokenAndIssuesNewAccessToken() {
		when(refreshTokenService.consume("old", client)).thenReturn(employee);
		when(userRepository.findWithAuthoritiesById(5L)).thenReturn(Optional.of(employee));

		AuthResult result = authService.refresh("old", client);

		assertThat(result.accessToken().value()).isEqualTo("jwt");
		assertThat(result.refreshToken().value()).isEqualTo("refresh");
		verify(refreshTokenService).issue(employee, client);
	}

	@Test
	void refreshWithoutCookieIsUnauthorized() {
		assertThatThrownBy(() -> authService.refresh(null, client)).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void refreshForDisabledUserIsUnauthorized() {
		employee.setStatus(UserStatus.DISABLED);
		when(refreshTokenService.consume("old", client)).thenReturn(employee);
		when(userRepository.findWithAuthoritiesById(5L)).thenReturn(Optional.of(employee));

		assertThatThrownBy(() -> authService.refresh("old", client)).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
		verify(refreshTokenService, never()).issue(any(), any());
	}

	@Test
	void logoutRevokesTokenAndAudits() {
		when(refreshTokenService.revoke("token")).thenReturn(Optional.of(5L));

		authService.logout("token", client);

		verify(auditService).record(eq(AuditAction.LOGOUT), eq(5L), eq("USER"), eq(5L), anyMap(), eq(client));
	}

	@Test
	void logoutWithoutCookieDoesNothing() {
		authService.logout(null, client);

		verify(refreshTokenService, never()).revoke(any());
	}

}
