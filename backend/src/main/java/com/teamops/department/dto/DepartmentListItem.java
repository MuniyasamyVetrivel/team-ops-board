package com.teamops.department.dto;

import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.user.dto.UserSummary;

/**
 * @param memberCount active users whose primary department this is
 * @param secondaryMemberCount active cross-department members
 */
public record DepartmentListItem(Long id, String name, String code, String description, DepartmentStatus status,
		UserSummary manager, long memberCount, long secondaryMemberCount) {

	public static DepartmentListItem of(Department department, long memberCount, long secondaryMemberCount) {
		return new DepartmentListItem(department.getId(), department.getName(), department.getCode(),
				department.getDescription(), department.getStatus(), UserSummary.of(department.getManager()),
				memberCount, secondaryMemberCount);
	}

}
