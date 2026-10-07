package com.teamops.project.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.project.dto.ProjectRef;
import com.teamops.project.service.ProjectService;

import lombok.RequiredArgsConstructor;

/** Project picker data for tasks. Full project management (and per-department scoping) arrives in Phase 8. */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

	private final ProjectService projectService;

	@GetMapping("/options")
	@PreAuthorize("hasAnyAuthority('PROJECT_VIEW', 'TASK_CREATE')")
	public List<ProjectRef> options() {
		return projectService.openProjects();
	}

}
