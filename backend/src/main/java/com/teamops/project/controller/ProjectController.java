package com.teamops.project.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.project.dto.ProjectDtos;
import com.teamops.project.dto.ProjectDtos.ProjectDetail;
import com.teamops.project.dto.ProjectDtos.ProjectListItem;
import com.teamops.project.dto.ProjectRef;
import com.teamops.project.entity.ProjectStatus;
import com.teamops.project.service.ProjectService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Project API. {@code @PreAuthorize} checks the permission; {@link ProjectService} checks scope on every call. */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("name", List.of("name"), "code", List.of("id"),
			"end", List.of("endDate", "id"), "start", List.of("startDate", "id"), "updated", List.of("updatedAt"),
			"status", List.of("status", "name"));

	private final ProjectService projectService;

	/** Project picker data for tasks. */
	@GetMapping("/options")
	@PreAuthorize("hasAnyAuthority('PROJECT_VIEW', 'TASK_CREATE')")
	public List<ProjectRef> options() {
		return projectService.openProjects();
	}

	@GetMapping
	@PreAuthorize("hasAuthority('PROJECT_VIEW')")
	public PageResponse<ProjectListItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<ProjectStatus> status,
			@RequestParam(required = false) Long departmentId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.search(search, status, departmentId,
				PageRequests.of(page, size, sort, SORT_FIELDS, "end,asc"), actor);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('PROJECT_VIEW')")
	public ProjectDetail get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.get(id, actor);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ResponseEntity<ProjectDetail> create(@Valid @RequestBody ProjectDtos.CreateProject request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(projectService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail update(@PathVariable Long id, @Valid @RequestBody ProjectDtos.UpdateProject request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return projectService.update(id, request, actor, ClientInfo.from(http));
	}

	@PostMapping("/{id}/members")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail addMember(@PathVariable Long id, @Valid @RequestBody ProjectDtos.Member request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.addMember(id, request.userId(), actor);
	}

	@DeleteMapping("/{id}/members/{userId}")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail removeMember(@PathVariable Long id, @PathVariable Long userId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.removeMember(id, userId, actor);
	}

	@PostMapping("/{id}/milestones")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail addMilestone(@PathVariable Long id, @Valid @RequestBody ProjectDtos.SaveMilestone request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.addMilestone(id, request, actor);
	}

	@PutMapping("/{id}/milestones/{milestoneId}")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail updateMilestone(@PathVariable Long id, @PathVariable Long milestoneId,
			@Valid @RequestBody ProjectDtos.SaveMilestone request, @AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.updateMilestone(id, milestoneId, request, actor);
	}

	@DeleteMapping("/{id}/milestones/{milestoneId}")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail deleteMilestone(@PathVariable Long id, @PathVariable Long milestoneId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.deleteMilestone(id, milestoneId, actor);
	}

	@PostMapping("/{id}/risks")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail addRisk(@PathVariable Long id, @Valid @RequestBody ProjectDtos.SaveRisk request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.addRisk(id, request, actor);
	}

	@PutMapping("/{id}/risks/{riskId}")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail updateRisk(@PathVariable Long id, @PathVariable Long riskId,
			@Valid @RequestBody ProjectDtos.SaveRisk request, @AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.updateRisk(id, riskId, request, actor);
	}

	@DeleteMapping("/{id}/risks/{riskId}")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail deleteRisk(@PathVariable Long id, @PathVariable Long riskId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.deleteRisk(id, riskId, actor);
	}

	@PostMapping("/{id}/dependencies")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail addDependency(@PathVariable Long id, @Valid @RequestBody ProjectDtos.Dependency request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.addDependency(id, request.projectId(), actor);
	}

	@DeleteMapping("/{id}/dependencies/{dependsOnId}")
	@PreAuthorize("hasAuthority('PROJECT_EDIT')")
	public ProjectDetail removeDependency(@PathVariable Long id, @PathVariable Long dependsOnId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return projectService.removeDependency(id, dependsOnId, actor);
	}

}
