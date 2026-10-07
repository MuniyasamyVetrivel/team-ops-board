package com.teamops.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@NotBlank(message = "New password is required") @Size(max = 128) String newPassword) {

	@Override
	public String toString() {
		return "ResetPasswordRequest[newPassword=***]";
	}

}
