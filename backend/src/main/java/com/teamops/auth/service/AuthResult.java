package com.teamops.auth.service;

import com.teamops.auth.dto.AuthResponse;
import com.teamops.auth.dto.MeResponse;
import com.teamops.common.security.AccessToken;

/** Outcome of a successful login or refresh: a new access token plus a rotated refresh token. */
public record AuthResult(AccessToken accessToken, IssuedRefreshToken refreshToken, MeResponse user) {

	public AuthResponse toResponse() {
		return new AuthResponse(accessToken.value(), "Bearer", accessToken.expiresInSeconds(),
				accessToken.expiresAt(), user);
	}

}
