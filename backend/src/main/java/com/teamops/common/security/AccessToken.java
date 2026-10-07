package com.teamops.common.security;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt, long expiresInSeconds) {

}
