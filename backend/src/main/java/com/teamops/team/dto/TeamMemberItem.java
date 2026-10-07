package com.teamops.team.dto;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;

/** Team directory row (brief section 18). {@code manager} is the person the employee reports to. */
public record TeamMemberItem(Long id, String fullName, String firstName, String lastName, String email,
		String jobTitle, DepartmentSummary department, UserSummary manager, String phone, String location,
		String workingHours, UserStatus status) {

	public static TeamMemberItem of(User user) {
		return new TeamMemberItem(user.getId(), user.getFullName(), user.getFirstName(), user.getLastName(),
				user.getEmail(), user.getJobTitle(), DepartmentSummary.of(user.getDepartment()),
				UserSummary.of(user.getReportsTo()), user.getPhone(), user.getLocation(), user.getWorkingHours(),
				user.getStatus());
	}

}
