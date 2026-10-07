package com.teamops.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
		@NotBlank(message = "Name is required") @Size(max = 100) String name,
		@NotBlank(message = "Code is required") @Pattern(regexp = "[A-Za-z0-9_]{2,40}", message = "Code must be 2-40 letters, digits or underscores") String code,
		@Size(max = 500) String description,
		Long managerId) {

}
