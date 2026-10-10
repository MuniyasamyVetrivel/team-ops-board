package com.teamops.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.support.SliceAuth;
import com.teamops.user.entity.RoleCodes;

class RolePermissionRulesTest {

	/** Every V2 permission and its module. */
	private static final Map<String, String> CATALOGUE = SliceAuth.ALL_PERMISSIONS.stream()
		.collect(Collectors.toMap(code -> code,
				code -> MarketingPermissionRules.MARKETING_PERMISSIONS.contains(code) ? "MARKETING" : "CORE"));

	/** Holds PERMISSION_MANAGE but is not a Super Admin. */
	private static final AuthenticatedUser MANAGER = new AuthenticatedUser(7L, "m@teamops.local", "Manager", 1L,
			Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of("PERMISSION_MANAGE", "TASK_VIEW", "WORKLOAD_VIEW"));

	@Test
	void theSuperAdminRoleIsLocked() {
		assertCode(() -> check(RoleCodes.SUPER_ADMIN, Set.of("TASK_VIEW"), SliceAuth.SUPER_ADMIN), "ROLE_LOCKED");
	}

	@Test
	void unknownPermissionsAreRejected() {
		assertCode(() -> check(RoleCodes.EMPLOYEE, Set.of("TASK_VIEW", "LAUNCH_ROCKETS"), SliceAuth.SUPER_ADMIN),
				"UNKNOWN_PERMISSION");
	}

	@Test
	void marketingPermissionsGoToPeopleNotRoles() {
		assertCode(() -> check(RoleCodes.EMPLOYEE, Set.of("TASK_VIEW", "MARKETING_VIEW"), SliceAuth.SUPER_ADMIN),
				"MARKETING_PERMISSION_ON_ROLE");
	}

	@Test
	void actionsNeedTheirViewPermission() {
		assertCode(() -> check(RoleCodes.EMPLOYEE, Set.of("TASK_EDIT"), SliceAuth.SUPER_ADMIN),
				"VIEW_PERMISSION_REQUIRED");
		assertCode(() -> check(RoleCodes.DEPARTMENT_MANAGER, Set.of("REPORT_EXPORT"), SliceAuth.SUPER_ADMIN),
				"VIEW_PERMISSION_REQUIRED");
		assertThatCode(() -> check(RoleCodes.EMPLOYEE, Set.of("TASK_VIEW", "TASK_EDIT", "WORKLOAD_VIEW"),
				SliceAuth.SUPER_ADMIN))
			.doesNotThrowAnyException();
	}

	@Test
	void noOneGrantsWhatTheyDoNotHold() {
		assertCode(() -> RolePermissionRules.check(RoleCodes.EMPLOYEE, Set.of("TASK_VIEW", "AUDIT_VIEW"),
				Set.of("TASK_VIEW"), CATALOGUE, MANAGER), "PERMISSION_NOT_HELD");
		// Keeping a permission the role already has is not a grant.
		assertThatCode(() -> RolePermissionRules.check(RoleCodes.EMPLOYEE, Set.of("TASK_VIEW", "KB_VIEW", "WORKLOAD_VIEW"),
				Set.of("TASK_VIEW", "KB_VIEW"), CATALOGUE, MANAGER))
			.doesNotThrowAnyException();
	}

	private static void check(String role, Set<String> requested, AuthenticatedUser actor) {
		RolePermissionRules.check(role, requested, Set.of(), CATALOGUE, actor);
	}

	private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
		assertThatThrownBy(call).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
	}

}
