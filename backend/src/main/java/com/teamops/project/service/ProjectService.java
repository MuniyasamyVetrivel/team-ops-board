package com.teamops.project.service;

import java.util.EnumSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.project.dto.ProjectRef;
import com.teamops.project.entity.ProjectStatus;
import com.teamops.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

	private static final EnumSet<ProjectStatus> OPEN = EnumSet.of(ProjectStatus.PLANNING, ProjectStatus.ACTIVE,
			ProjectStatus.ON_HOLD);

	private final ProjectRepository projectRepository;

	/** Projects that can still take new tasks. */
	public List<ProjectRef> openProjects() {
		return projectRepository.findByStatusInOrderByNameAsc(OPEN).stream().map(ProjectRef::of).toList();
	}

}
