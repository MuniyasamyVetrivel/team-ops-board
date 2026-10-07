package com.teamops.auth.dto;

import java.time.Instant;

/** Returned by login and refresh. The refresh token travels only in an httpOnly cookie, never in the body. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt, MeResponse user) {

}
