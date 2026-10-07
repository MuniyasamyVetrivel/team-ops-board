package com.teamops.auth.dto;

import java.util.List;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.entity.User;

/** The signed-in user's profile and effective authorities, as computed by the backend. */
public record MeResponse(
		Long id,
		String email,
		String firstName,
		String lastName,
		String fullName,
		String jobTitle,
		DepartmentSummary department,
		List<String> roles,
		List<String> permissions) {

	public static MeResponse of(User user, AuthenticatedUser principal) {
		return new MeResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
				user.getFullName(), user.getJobTitle(), DepartmentSummary.of(user.getDepartment()),
				principal.roles().stream().sorted().toList(), principal.permissions().stream().sorted().toList());
	}

}
