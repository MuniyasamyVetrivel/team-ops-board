package com.teamops.team.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageResponse;
import com.teamops.team.dto.TeamMemberItem;
import com.teamops.team.dto.TeamMemberProfile;
import com.teamops.user.dto.UserSearchCriteria;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.repository.UserSpecifications;

import lombok.RequiredArgsConstructor;

/** Company-wide team directory. Contact details are visible to everyone with TEAM_VIEW; work data is scoped. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

	private final UserRepository userRepository;

	private final AccessScopeService accessScopeService;

	public PageResponse<TeamMemberItem> directory(UserSearchCriteria criteria, Pageable pageable) {
		var spec = UserSpecifications.matches(criteria.search())
			.and(UserSpecifications.inDepartment(criteria.departmentId()))
			.and(UserSpecifications.withStatus(criteria.status()));
		return PageResponse.of(userRepository.findAll(spec, pageable).map(TeamMemberItem::of));
	}

	public TeamMemberProfile profile(Long id, AuthenticatedUser viewer) {
		User user = userRepository.findWithDepartmentById(id)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Team member not found"));
		return new TeamMemberProfile(TeamMemberItem.of(user),
				user.getRoles().stream().map(Role::getCode).sorted().toList(), user.getCreatedAt(),
				userRepository.findByReportsToIdOrderByFirstNameAscLastNameAsc(id)
					.stream()
					.map(UserSummary::of)
					.toList(),
				accessScopeService.canViewWorkOf(viewer, user.getId(), user.getDepartment().getId()));
	}

}
