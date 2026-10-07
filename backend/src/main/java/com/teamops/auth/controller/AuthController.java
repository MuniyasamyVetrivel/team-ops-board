package com.teamops.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.auth.dto.AuthResponse;
import com.teamops.auth.dto.LoginRequest;
import com.teamops.auth.dto.MeResponse;
import com.teamops.auth.service.AuthResult;
import com.teamops.auth.service.AuthService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	private final RefreshCookieManager refreshCookieManager;

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return withRefreshCookie(authService.login(request, ClientInfo.from(http)));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
		String token = refreshCookieManager.read(http).orElse(null);
		return withRefreshCookie(authService.refresh(token, ClientInfo.from(http)));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest http) {
		authService.logout(refreshCookieManager.read(http).orElse(null), ClientInfo.from(http));
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, refreshCookieManager.clear().toString())
			.build();
	}

	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		return authService.me(user.id());
	}

	private ResponseEntity<AuthResponse> withRefreshCookie(AuthResult result) {
		String cookie = refreshCookieManager
			.create(result.refreshToken().value(), result.refreshToken().expiresAt())
			.toString();
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie).body(result.toResponse());
	}

}
