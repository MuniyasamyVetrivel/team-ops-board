package com.teamops.department.dto;

import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;

/**
 * A person in a department.
 * @param membership PRIMARY (users.department_id), or MANAGER / MEMBER for secondary memberships
 * @param primaryDepartment the user's primary department (differs from this department for secondary members)
 */
public record DepartmentMemberItem(UserSummary user, String membership, DepartmentSummary primaryDepartment) {

	public static final String PRIMARY = "PRIMARY";

	public static DepartmentMemberItem of(User user, String membership) {
		return new DepartmentMemberItem(UserSummary.of(user), membership, DepartmentSummary.of(user.getDepartment()));
	}

}
