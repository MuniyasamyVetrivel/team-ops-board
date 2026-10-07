package com.teamops.team.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.team.dto.TeamMemberItem;
import com.teamops.team.dto.TeamMemberProfile;
import com.teamops.team.service.TeamService;
import com.teamops.user.dto.UserSearchCriteria;
import com.teamops.user.entity.UserStatus;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TEAM_VIEW')")
public class TeamController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("name", List.of("firstName", "lastName"),
			"department", List.of("department.name", "firstName"), "jobTitle", List.of("jobTitle"));

	private final TeamService teamService;

	/** Active people by default; pass {@code status=DISABLED} to see former staff. */
	@GetMapping
	public PageResponse<TeamMemberItem> directory(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long departmentId,
			@RequestParam(defaultValue = "ACTIVE") UserStatus status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "24") int size, @RequestParam(required = false) String sort) {
		return teamService.directory(new UserSearchCriteria(search, departmentId, status, null),
				PageRequests.of(page, size, sort, SORT_FIELDS, "name,asc"));
	}

	@GetMapping("/{id}")
	public TeamMemberProfile profile(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return teamService.profile(id, viewer);
	}

}
