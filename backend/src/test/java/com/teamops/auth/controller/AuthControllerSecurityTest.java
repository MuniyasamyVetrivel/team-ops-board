package com.teamops.auth.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.auth.dto.MeResponse;
import com.teamops.auth.service.AuthResult;
import com.teamops.auth.service.AuthService;
import com.teamops.auth.service.IssuedRefreshToken;
import com.teamops.common.config.ClockConfig;
import com.teamops.common.exception.ErrorResponseWriter;
import com.teamops.common.security.AccessToken;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtConfig;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.RestAccessDeniedHandler;
import com.teamops.common.security.RestAuthenticationEntryPoint;
import com.teamops.common.security.SecurityConfig;
import com.teamops.common.security.SecurityProperties;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.support.TestFixtures;

import jakarta.servlet.http.Cookie;

/** Security filter chain + auth endpoints with mocked services. No database required. */
@WebMvcTest(controllers = AuthController.class)
@Import({ SecurityConfig.class, JwtConfig.class, JwtTokenService.class, RestAuthenticationEntryPoint.class,
		RestAccessDeniedHandler.class, ErrorResponseWriter.class, RefreshCookieManager.class, ClockConfig.class,
		AuthControllerSecurityTest.ProbeController.class })
@TestPropertySource(properties = { "app.security.jwt.secret=" + TestFixtures.JWT_SECRET,
		"app.security.jwt.access-token-ttl=60", "app.security.jwt.refresh-token-ttl=7",
		"app.security.refresh-cookie.secure=false", "app.security.cors.allowed-origins=http://localhost:5173" })
class AuthControllerSecurityTest {

	private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "rakesh@teamops.local", "Rakesh", 1L,
			Set.of("SUPER_ADMIN"), Set.of("USER_MANAGE", "TASK_VIEW"));

	private static final AuthenticatedUser EMPLOYEE = new AuthenticatedUser(4L, "karthik.raj@teamops.local",
			"Karthik Raj", 5L, Set.of("EMPLOYEE"), Set.of("TASK_VIEW"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@Test
	void meWithoutTokenIs401WithJsonError() throws Exception {
		mvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.path").value("/api/auth/me"));
	}

	@Test
	void meWithGarbageTokenIs401() throws Exception {
		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void meWithExpiredTokenIs401() throws Exception {
		SecurityProperties properties = TestFixtures.securityProperties();
		JwtConfig jwtConfig = new JwtConfig();
		JwtTokenService pastIssuer = new JwtTokenService(jwtConfig.jwtEncoder(jwtConfig.jwtSigningKey(properties)),
				properties, Clock.offset(Clock.systemUTC(), Duration.ofHours(-2)));
		String expired = pastIssuer.issue(EMPLOYEE).value();

		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void meWithValidTokenReturnsProfile() throws Exception {
		when(userPrincipalService.findActiveUser(4L)).thenReturn(Optional.of(EMPLOYEE));
		when(authService.me(4L)).thenReturn(me(EMPLOYEE));

		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(EMPLOYEE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("karthik.raj@teamops.local"))
			.andExpect(jsonPath("$.permissions[0]").value("TASK_VIEW"));
	}

	@Test
	void validTokenOfDisabledOrDeletedUserIs401() throws Exception {
		when(userPrincipalService.findActiveUser(anyLong())).thenReturn(Optional.empty());

		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(EMPLOYEE)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void permissionProtectedEndpointIs403ForEmployee() throws Exception {
		when(userPrincipalService.findActiveUser(4L)).thenReturn(Optional.of(EMPLOYEE));

		mvc.perform(get("/api/test/admin-only").header(HttpHeaders.AUTHORIZATION, bearer(EMPLOYEE)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void permissionProtectedEndpointIs200ForSuperAdmin() throws Exception {
		when(userPrincipalService.findActiveUser(1L)).thenReturn(Optional.of(ADMIN));

		mvc.perform(get("/api/test/admin-only").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
			.andExpect(status().isOk());
	}

	@Test
	void permissionsAreReadFromDatabaseNotFromTokenClaims() throws Exception {
		// Token was issued while the user was an admin; the database now says they are an employee.
		when(userPrincipalService.findActiveUser(1L)).thenReturn(Optional.of(new AuthenticatedUser(1L,
				"rakesh@teamops.local", "Rakesh", 1L, Set.of("EMPLOYEE"), Set.of("TASK_VIEW"))));

		mvc.perform(get("/api/test/admin-only").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
			.andExpect(status().isForbidden());
	}

	@Test
	void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
		Instant now = Instant.now();
		when(authService.login(any(), any())).thenReturn(new AuthResult(
				new AccessToken("access-jwt", now.plusSeconds(3600), 3600),
				new IssuedRefreshToken("refresh-raw", now.plus(Duration.ofDays(7))), me(EMPLOYEE)));

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"karthik.raj@teamops.local\",\"password\":\"secret-pass\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("access-jwt"))
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(3600))
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(containsString("tob_refresh=refresh-raw"),
					containsString("HttpOnly"), containsString("SameSite=Strict"), containsString("Path=/api/auth"))));
	}

	@Test
	void loginIgnoresStaleBearerHeader() throws Exception {
		Instant now = Instant.now();
		when(authService.login(any(), any())).thenReturn(new AuthResult(
				new AccessToken("access-jwt", now.plusSeconds(3600), 3600),
				new IssuedRefreshToken("refresh-raw", now.plus(Duration.ofDays(7))), me(EMPLOYEE)));

		mvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-garbage")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"karthik.raj@teamops.local\",\"password\":\"secret-pass\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void loginWithInvalidBodyIs400WithFieldErrors() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.length()").value(2));
		verifyNoInteractions(authService);
	}

	@Test
	void loginWithMalformedJsonIs400() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{oops"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void refreshReadsTokenFromCookie() throws Exception {
		Instant now = Instant.now();
		when(authService.refresh(org.mockito.ArgumentMatchers.eq("cookie-value"), any())).thenReturn(new AuthResult(
				new AccessToken("new-jwt", now.plusSeconds(3600), 3600),
				new IssuedRefreshToken("rotated", now.plus(Duration.ofDays(7))), me(EMPLOYEE)));

		mvc.perform(post("/api/auth/refresh").cookie(new Cookie("tob_refresh", "cookie-value")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("new-jwt"))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("tob_refresh=rotated")));
	}

	@Test
	void logoutClearsCookieEvenWithoutSession() throws Exception {
		mvc.perform(post("/api/auth/logout"))
			.andExpect(status().isNoContent())
			.andExpect(header().string(HttpHeaders.SET_COOKIE,
					allOf(containsString("tob_refresh="), containsString("Max-Age=0"))));
	}

	@Test
	void corsPreflightAllowsConfiguredOrigin() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/auth/login")
			.header(HttpHeaders.ORIGIN, "http://localhost:5173")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
	}

	@Test
	void corsPreflightRejectsUnknownOrigin() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/auth/login")
			.header(HttpHeaders.ORIGIN, "https://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden());
	}

	private String bearer(AuthenticatedUser user) {
		return "Bearer " + jwtTokenService.issue(user).value();
	}

	private static MeResponse me(AuthenticatedUser user) {
		return new MeResponse(user.id(), user.email(), "Test", "User", user.fullName(), "Engineer",
				new DepartmentSummary(user.departmentId(), "Web Development", "WEBDEV"),
				List.copyOf(user.roles()), user.permissions().stream().sorted().toList());
	}

	/** Test-only endpoint that proves method security (@PreAuthorize) is active. */
	@RestController
	static class ProbeController {

		@GetMapping("/api/test/admin-only")
		@PreAuthorize("hasAuthority('USER_MANAGE')")
		String adminOnly() {
			return "ok";
		}

	}

}
