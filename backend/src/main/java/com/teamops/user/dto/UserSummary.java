package com.teamops.user.dto;

import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;

/**
 * Compact reference to a user (manager, reports-to, member lists, every avatar). {@code employeeId} is visible to
 * everyone signed in; {@code photoVersion} is null without a profile photo, else it changes with every new photo
 * (fetch it from {@code GET /api/users/{id}/photo}).
 */
public record UserSummary(Long id, String fullName, String email, String jobTitle, UserStatus status,
		String employeeId, Long photoVersion) {

	public static UserSummary of(User user) {
		return user == null ? null
				: new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getJobTitle(),
						user.getStatus(), user.getEmployeeId(), user.getPhotoFileId());
	}

}
