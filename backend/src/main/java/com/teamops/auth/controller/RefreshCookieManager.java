package com.teamops.auth.controller;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import com.teamops.common.security.SecurityProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** Reads and writes the httpOnly refresh-token cookie (scoped to /api/auth, SameSite=Strict). */
@Component
@RequiredArgsConstructor
public class RefreshCookieManager {

	private final SecurityProperties securityProperties;

	private final Clock clock;

	public Optional<String> read(HttpServletRequest request) {
		Cookie cookie = WebUtils.getCookie(request, settings().name());
		return Optional.ofNullable(cookie).map(Cookie::getValue);
	}

	public ResponseCookie create(String token, Instant expiresAt) {
		Duration maxAge = Duration.between(clock.instant(), expiresAt);
		return builder(token).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
	}

	public ResponseCookie clear() {
		return builder("").maxAge(Duration.ZERO).build();
	}

	private ResponseCookie.ResponseCookieBuilder builder(String value) {
		SecurityProperties.RefreshCookie settings = settings();
		return ResponseCookie.from(settings.name(), value)
			.httpOnly(true)
			.secure(settings.secure())
			.sameSite(settings.sameSite())
			.path(settings.path());
	}

	private SecurityProperties.RefreshCookie settings() {
		return securityProperties.refreshCookie();
	}

}
