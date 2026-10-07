package com.teamops.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.teamops.auth.entity.RefreshToken;
import com.teamops.auth.repository.RefreshTokenRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.web.ClientInfo;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;

class RefreshTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);

	private final AuditService auditService = mock(AuditService.class);

	private final RefreshTokenService service = new RefreshTokenService(repository,
			TestFixtures.securityProperties(), auditService, Clock.fixed(NOW, ZoneOffset.UTC));

	private final User user = TestFixtures.user(9L, "karthik.raj@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));

	private final ClientInfo client = new ClientInfo("127.0.0.1", "JUnit");

	@Test
	void issueStoresOnlyTheHashWithSevenDayExpiry() {
		IssuedRefreshToken issued = service.issue(user, client);

		ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(issued.value()))
			.isNotEqualTo(issued.value())
			.hasSize(64);
		assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		assertThat(saved.getValue().getIpAddress()).isEqualTo("127.0.0.1");
	}

	@Test
	void consumeRevokesAValidTokenAndReturnsItsUser() {
		RefreshToken token = token(NOW.plus(Duration.ofDays(1)), null);
		when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));

		User owner = service.consume("raw", client);

		assertThat(owner).isSameAs(user);
		assertThat(token.getRevokedAt()).isEqualTo(NOW);
	}

	@Test
	void consumeRejectsUnknownToken() {
		when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

		assertUnauthorized(() -> service.consume("nope", client));
	}

	@Test
	void consumeRejectsExpiredToken() {
		when(repository.findByTokenHash(any())).thenReturn(Optional.of(token(NOW.minusSeconds(1), null)));

		assertUnauthorized(() -> service.consume("raw", client));
	}

	@Test
	void reuseOfRevokedTokenRevokesAllSessionsOfThatUser() {
		when(repository.findByTokenHash(any())).thenReturn(Optional.of(token(NOW.plus(Duration.ofDays(1)),
				NOW.minus(Duration.ofMinutes(5)))));

		assertUnauthorized(() -> service.consume("stolen", client));

		verify(repository).revokeAllActiveForUser(9L, NOW);
		verify(auditService).record(eq(AuditAction.REFRESH_TOKEN_REUSE), eq(9L), eq("USER"), eq(9L), any(),
				eq(client));
	}

	@Test
	void concurrentRefreshWithinGracePeriodIsRejectedWithoutRevokingEverything() {
		when(repository.findByTokenHash(any())).thenReturn(Optional.of(token(NOW.plus(Duration.ofDays(1)),
				NOW.minusSeconds(2))));

		assertUnauthorized(() -> service.consume("raw", client));

		verify(repository, never()).revokeAllActiveForUser(anyLong(), any());
	}

	@Test
	void revokeReturnsOwnerOnlyForActiveTokens() {
		RefreshToken active = token(NOW.plus(Duration.ofDays(1)), null);
		when(repository.findByTokenHash(RefreshTokenService.hash("active"))).thenReturn(Optional.of(active));
		when(repository.findByTokenHash(RefreshTokenService.hash("revoked")))
			.thenReturn(Optional.of(token(NOW.plus(Duration.ofDays(1)), NOW.minusSeconds(60))));

		assertThat(service.revoke("active")).contains(9L);
		assertThat(active.getRevokedAt()).isEqualTo(NOW);
		assertThat(service.revoke("revoked")).isEmpty();
	}

	private RefreshToken token(Instant expiresAt, Instant revokedAt) {
		RefreshToken token = new RefreshToken();
		token.setId(1L);
		token.setUser(user);
		token.setTokenHash("hash");
		token.setExpiresAt(expiresAt);
		token.setRevokedAt(revokedAt);
		return token;
	}

	private static void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
	}

}
