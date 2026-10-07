package com.teamops.user.dto;

import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;

/** Compact reference to a user (manager, reports-to, member lists). */
public record UserSummary(Long id, String fullName, String email, String jobTitle, UserStatus status) {

	public static UserSummary of(User user) {
		return user == null ? null
				: new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getJobTitle(),
						user.getStatus());
	}

}
