package com.teamops.user.service;

import java.util.Set;

/** Input for {@link UserProvisioningService#createUser}. Passwords are raw and hashed by the service. */
public record NewUser(String email, String rawPassword, String firstName, String lastName, String jobTitle,
		String departmentCode, Set<String> roleCodes, Set<String> permissionCodes) {

	public NewUser {
		roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
		permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
	}

	@Override
	public String toString() {
		return "NewUser[email=" + email + ", departmentCode=" + departmentCode + ", roles=" + roleCodes + "]";
	}

}
