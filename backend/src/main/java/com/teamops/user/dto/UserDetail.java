package com.teamops.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.entity.Permission;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;

/** Full user record for the admin screens, including role, direct and effective permissions. */
public record UserDetail(
		Long id,
		String email,
		String firstName,
		String lastName,
		String fullName,
		String jobTitle,
		String phone,
		String location,
		String workingHours,
		BigDecimal weeklyCapacityHours,
		DepartmentSummary department,
		UserSummary reportsTo,
		UserStatus status,
		Instant lastLoginAt,
		Instant createdAt,
		List<String> roles,
		List<String> directPermissions,
		List<String> effectivePermissions) {

	/** The user must have roles, role permissions and direct permissions loaded. */
	public static UserDetail of(User user, AuthenticatedUser effective) {
		return new UserDetail(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
				user.getFullName(), user.getJobTitle(), user.getPhone(), user.getLocation(), user.getWorkingHours(),
				user.getWeeklyCapacityHours(), DepartmentSummary.of(user.getDepartment()),
				UserSummary.of(user.getReportsTo()), user.getStatus(), user.getLastLoginAt(), user.getCreatedAt(),
				user.getRoles().stream().map(Role::getCode).sorted().toList(),
				user.getDirectPermissions().stream().map(Permission::getCode).sorted().toList(),
				effective.permissions().stream().sorted().toList());
	}

}
