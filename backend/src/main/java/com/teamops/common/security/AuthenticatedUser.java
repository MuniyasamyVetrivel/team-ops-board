package com.teamops.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.teamops.user.entity.RoleCodes;

/**
 * The current user as resolved by the backend from the database. Inject with
 * {@code @AuthenticationPrincipal AuthenticatedUser user}. Never built from client-supplied data.
 */
public record AuthenticatedUser(Long id, String email, String fullName, Long departmentId, Set<String> roles,
		Set<String> permissions) {

	public AuthenticatedUser {
		roles = Set.copyOf(roles);
		permissions = Set.copyOf(permissions);
	}

	public boolean isSuperAdmin() {
		return roles.contains(RoleCodes.SUPER_ADMIN);
	}

	public boolean hasRole(String roleCode) {
		return roles.contains(roleCode);
	}

	public boolean hasPermission(String permissionCode) {
		return permissions.contains(permissionCode);
	}

	/** Roles as {@code ROLE_*} authorities, permissions as plain codes (for {@code hasAuthority('TASK_EDIT')}). */
	public List<GrantedAuthority> authorities() {
		List<GrantedAuthority> authorities = new ArrayList<>(roles.size() + permissions.size());
		roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
		permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
		return authorities;
	}

}
