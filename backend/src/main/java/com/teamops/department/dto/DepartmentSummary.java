package com.teamops.department.dto;

import com.teamops.department.entity.Department;

public record DepartmentSummary(Long id, String name, String code) {

	public static DepartmentSummary of(Department department) {
		return new DepartmentSummary(department.getId(), department.getName(), department.getCode());
	}

}
