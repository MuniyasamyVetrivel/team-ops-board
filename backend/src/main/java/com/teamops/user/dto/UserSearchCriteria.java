package com.teamops.user.dto;

import com.teamops.user.entity.UserStatus;

/**
 * Optional filters for user lists. Null means "any". {@code missingEmployeeId}: only users created before employee
 * IDs existed and not given one yet.
 */
public record UserSearchCriteria(String search, Long departmentId, UserStatus status, String role,
		boolean missingEmployeeId) {

	public UserSearchCriteria(String search, Long departmentId, UserStatus status, String role) {
		this(search, departmentId, status, role, false);
	}

}
