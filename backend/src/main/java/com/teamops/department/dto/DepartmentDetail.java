package com.teamops.department.dto;

import java.time.Instant;
import java.util.List;

import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.user.dto.UserSummary;

/**
 * @param canEdit viewer may edit the department itself (DEPARTMENT_MANAGE)
 * @param canManageMembers viewer may add/remove secondary members (DEPARTMENT_MANAGE or manager of this department)
 */
public record DepartmentDetail(Long id, String name, String code, String description, DepartmentStatus status,
		UserSummary manager, Instant createdAt, List<DepartmentMemberItem> members, boolean canEdit,
		boolean canManageMembers) {

	public static DepartmentDetail of(Department department, List<DepartmentMemberItem> members, boolean canEdit,
			boolean canManageMembers) {
		return new DepartmentDetail(department.getId(), department.getName(), department.getCode(),
				department.getDescription(), department.getStatus(), UserSummary.of(department.getManager()),
				department.getCreatedAt(), members, canEdit, canManageMembers);
	}

}
