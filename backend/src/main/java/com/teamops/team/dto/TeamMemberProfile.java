package com.teamops.team.dto;

import java.time.Instant;
import java.util.List;

import com.teamops.user.dto.UserSummary;

/**
 * Employee profile. Work sections (tasks, workload, tickets, activity) are added by later phases and are only shown
 * when {@code canViewWork} is true, as decided by the viewer's access scope.
 */
public record TeamMemberProfile(TeamMemberItem member, List<String> roles, Instant memberSince,
		List<UserSummary> directReports, boolean canViewWork) {

}
