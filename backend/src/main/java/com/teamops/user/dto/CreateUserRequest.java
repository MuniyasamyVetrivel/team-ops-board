package com.teamops.user.dto;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A new user. The employee ID is required; once set, only a Super Admin can change it. */
public record CreateUserRequest(
		@NotBlank(message = "Employee ID is required") @Size(max = 40) String employeeId,
		@NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 255) String email,
		@NotBlank(message = "Password is required") @Size(max = 128) String password,
		@NotBlank(message = "First name is required") @Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@Size(max = 150) String jobTitle,
		@Size(max = 40) String phone,
		@Size(max = 150) String location,
		@Size(max = 100) String workingHours,
		@Size(max = 64) String timeZone,
		@NotNull(message = "Department is required") Long departmentId,
		Long reportsToId,
		@DecimalMin(value = "1", message = "Capacity must be at least 1 hour") @DecimalMax(value = "80", message = "Capacity cannot exceed 80 hours") BigDecimal weeklyCapacityHours,
		@NotEmpty(message = "At least one role is required") Set<String> roles,
		Set<String> permissions) {

	public CreateUserRequest {
		roles = roles == null ? Set.of() : Set.copyOf(roles);
		permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
	}

	@Override
	public String toString() {
		return "CreateUserRequest[employeeId=" + employeeId + ", email=" + email + ", departmentId=" + departmentId
				+ ", roles=" + roles + "]";
	}

}
