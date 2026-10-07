package com.teamops.department.dto;

import com.teamops.department.entity.DepartmentStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The department code is immutable: it is referenced by configuration and seed data. */
public record UpdateDepartmentRequest(
		@NotBlank(message = "Name is required") @Size(max = 100) String name,
		@Size(max = 500) String description,
		Long managerId,
		@NotNull(message = "Status is required") DepartmentStatus status) {

}
