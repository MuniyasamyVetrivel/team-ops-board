package com.teamops.common.security;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code app.security.*} - values come from backend/.env or real environment variables. */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull RefreshCookie refreshCookie,
		@Valid @NotNull Cors cors) {

	/**
	 * @param accessTokenTtl plain numbers are minutes
	 * @param refreshTokenTtl plain numbers are days
	 */
	public record Jwt(@NotBlank String secret, @NotBlank String issuer,
			@NotNull @DurationUnit(ChronoUnit.MINUTES) Duration accessTokenTtl,
			@NotNull @DurationUnit(ChronoUnit.DAYS) Duration refreshTokenTtl) {
	}

	public record RefreshCookie(@NotBlank String name, @NotBlank String path, boolean secure,
			@NotBlank String sameSite) {
	}

	public record Cors(List<String> allowedOrigins) {

		public Cors {
			allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
		}

	}

}
