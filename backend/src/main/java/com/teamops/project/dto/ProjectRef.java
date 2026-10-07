package com.teamops.project.dto;

import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectStatus;

/** Compact project reference for pickers and task rows. */
public record ProjectRef(Long id, String code, String name, Long departmentId, ProjectStatus status) {

	public static ProjectRef of(Project project) {
		return project == null ? null
				: new ProjectRef(project.getId(), project.getCode(), project.getName(), project.getDepartment().getId(),
						project.getStatus());
	}

}
