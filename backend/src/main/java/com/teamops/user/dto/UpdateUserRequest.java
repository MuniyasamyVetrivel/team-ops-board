package com.teamops.user.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Profile fields an administrator may edit. Roles, permissions, status and password have their own endpoints. */
public record UpdateUserRequest(
		@NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 255) String email,
		@NotBlank(message = "First name is required") @Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@Size(max = 150) String jobTitle,
		@Size(max = 40) String phone,
		@Size(max = 150) String location,
		@Size(max = 100) String workingHours,
		@NotNull(message = "Department is required") Long departmentId,
		Long reportsToId,
		@NotNull(message = "Weekly capacity is required") @DecimalMin(value = "1", message = "Capacity must be at least 1 hour") @DecimalMax(value = "80", message = "Capacity cannot exceed 80 hours") BigDecimal weeklyCapacityHours) {

}
