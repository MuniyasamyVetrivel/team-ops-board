package com.teamops.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A signed-in user replacing their own password. */
public record ChangePasswordRequest(
		@NotBlank(message = "Current password is required") @Size(max = 128) String currentPassword,
		@NotBlank(message = "New password is required") @Size(max = 128) String newPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[***]";
	}

}
