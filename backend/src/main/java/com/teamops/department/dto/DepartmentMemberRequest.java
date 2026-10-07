package com.teamops.department.dto;

import com.teamops.department.entity.DepartmentMemberRole;

import jakarta.validation.constraints.NotNull;

public record DepartmentMemberRequest(@NotNull(message = "Member role is required") DepartmentMemberRole role) {

}
