package com.teamops.user.dto;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The complete new permission set of a role, with the version the editor loaded. */
public record UpdateRolePermissionsRequest(@NotNull(message = "Version is required") Integer version,
		@NotNull(message = "Permissions are required") @Size(max = 200, message = "Too many permissions") Set<@NotNull String> permissions) {

}
