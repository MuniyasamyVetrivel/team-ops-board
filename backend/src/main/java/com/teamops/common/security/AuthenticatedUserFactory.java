package com.teamops.common.security;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.teamops.user.entity.Permission;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Resolves effective permissions: SUPER_ADMIN gets every permission in the catalogue; everyone else gets the union
 * of their role permissions and direct grants.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedUserFactory {

	private final PermissionRepository permissionRepository;

	/** The user must have department, roles, role permissions and direct permissions loaded. */
	public AuthenticatedUser from(User user) {
		Set<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
		Set<String> permissions = roles.contains(RoleCodes.SUPER_ADMIN)
				? Set.copyOf(permissionRepository.findAllCodes())
				: Stream
					.concat(user.getRoles().stream().flatMap(role -> role.getPermissions().stream()),
							user.getDirectPermissions().stream())
					.map(Permission::getCode)
					.collect(Collectors.toSet());
		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(), user.getDepartment().getId(),
				roles, permissions);
	}

}
