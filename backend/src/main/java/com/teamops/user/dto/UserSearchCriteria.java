package com.teamops.user.dto;

import com.teamops.user.entity.UserStatus;

/** Optional filters for user lists. Null means "any". */
public record UserSearchCriteria(String search, Long departmentId, UserStatus status, String role) {

}
