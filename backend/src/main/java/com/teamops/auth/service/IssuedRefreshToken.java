package com.teamops.auth.service;

import java.time.Instant;

/** The raw refresh token (sent to the browser once, as a cookie) and its expiry. */
public record IssuedRefreshToken(String value, Instant expiresAt) {

}
