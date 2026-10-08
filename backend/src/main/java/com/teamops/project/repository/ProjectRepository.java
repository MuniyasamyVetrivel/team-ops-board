package com.teamops.project.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectStatus;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

	@EntityGraph(attributePaths = "department")
	List<Project> findByStatusInOrderByNameAsc(Collection<ProjectStatus> statuses);

	/** Paged search; members and dependencies are batch-loaded only when needed. */
	@Override
	@EntityGraph(attributePaths = { "department", "owner" })
	Page<Project> findAll(Specification<Project> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "department", "owner" })
	Optional<Project> findDetailedById(Long id);

	/** Is {@code from} (transitively) depending on {@code to}? Used to reject dependency cycles. */
	@Query(value = """
			with recursive chain (id) as (
			  select depends_on_project_id from project_dependencies where project_id = :from
			  union
			  select d.depends_on_project_id from project_dependencies d join chain c on d.project_id = c.id
			)
			select count(*) from chain where id = :to
			""", nativeQuery = true)
	long countDependencyPath(@Param("from") Long from, @Param("to") Long to);

}
