package com.teamops.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.project.entity.ProjectRisk;

public interface ProjectRiskRepository extends JpaRepository<ProjectRisk, Long> {

	@EntityGraph(attributePaths = "owner")
	List<ProjectRisk> findByProjectIdOrderByIdAsc(Long projectId);

}
