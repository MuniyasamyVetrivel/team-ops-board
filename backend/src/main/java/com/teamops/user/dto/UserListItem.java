package com.teamops.user.dto;

import java.time.Instant;
import java.util.List;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;

/** Row in the admin user list. */
public record UserListItem(Long id, String email, String firstName, String lastName, String fullName,
		String jobTitle, DepartmentSummary department, List<String> roles, UserStatus status, Instant lastLoginAt) {

	public static UserListItem of(User user) {
		return new UserListItem(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
				user.getFullName(), user.getJobTitle(), DepartmentSummary.of(user.getDepartment()),
				user.getRoles().stream().map(Role::getCode).sorted().toList(), user.getStatus(),
				user.getLastLoginAt());
	}

}
