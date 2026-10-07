package com.teamops.user.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

/** Replaces the user's roles and direct permission grants. */
public record UpdateUserAccessRequest(@NotEmpty(message = "At least one role is required") Set<String> roles,
		Set<String> permissions) {

	public UpdateUserAccessRequest {
		roles = roles == null ? Set.of() : Set.copyOf(roles);
		permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
	}

}
