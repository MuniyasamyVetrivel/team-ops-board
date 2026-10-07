package com.teamops.project.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectStatus;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	@EntityGraph(attributePaths = "department")
	List<Project> findByStatusInOrderByNameAsc(Collection<ProjectStatus> statuses);

}
