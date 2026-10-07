package com.teamops.support;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.security.SecurityProperties;
import com.teamops.department.entity.Department;
import com.teamops.user.entity.Permission;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;

/** Builders for entities and settings used by unit tests (no database). */
public final class TestFixtures {

	public static final String JWT_SECRET = "unit-test-secret-that-is-definitely-longer-than-32-bytes";

	private TestFixtures() {
	}

	public static SecurityProperties securityProperties() {
		return new SecurityProperties(
				new SecurityProperties.Jwt(JWT_SECRET, "team-ops-board", Duration.ofMinutes(60), Duration.ofDays(7)),
				new SecurityProperties.RefreshCookie("tob_refresh", "/api/auth", false, "Strict"),
				new SecurityProperties.Cors(List.of("http://localhost:5173")));
	}

	public static Department department(long id, String code, String name) {
		Department department = new Department();
		ReflectionTestUtils.setField(department, "id", id);
		department.setCode(code);
		department.setName(name);
		return department;
	}

	public static Permission permission(long id, String code) {
		Permission permission = new Permission();
		permission.setId(id);
		permission.setCode(code);
		permission.setName(code);
		permission.setModule("TEST");
		return permission;
	}

	public static Role role(long id, String code, String... permissionCodes) {
		Role role = new Role();
		role.setId(id);
		role.setCode(code);
		role.setName(code);
		role.setPermissions(permissions(permissionCodes));
		return role;
	}

	public static User user(long id, String email, Role role, String... directPermissionCodes) {
		User user = new User();
		ReflectionTestUtils.setField(user, "id", id);
		user.setEmail(email);
		user.setFirstName("Test");
		user.setLastName("User");
		user.setPasswordHash("$2a$12$hash");
		user.setStatus(UserStatus.ACTIVE);
		user.setDepartment(department(7L, "DM", "Digital Marketing"));
		user.setRoles(new java.util.HashSet<>(Set.of(role)));
		user.setDirectPermissions(permissions(directPermissionCodes));
		return user;
	}

	private static Set<Permission> permissions(String... codes) {
		long[] counter = { 100 };
		return Arrays.stream(codes)
			.map(code -> permission(counter[0]++, code))
			.collect(Collectors.toCollection(java.util.HashSet::new));
	}

}
