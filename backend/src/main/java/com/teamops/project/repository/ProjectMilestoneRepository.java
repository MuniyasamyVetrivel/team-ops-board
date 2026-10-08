package com.teamops.project.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.project.entity.ProjectMilestone;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, Long> {

	List<ProjectMilestone> findByProjectIdOrderByPositionAscIdAsc(Long projectId);

	/** Milestones not yet completed, due in [from, to], with their project (calendar). */
	@Query("""
			select m from ProjectMilestone m join fetch m.project p join fetch p.department left join fetch p.owner
			where m.dueDate between :from and :to and m.status <> com.teamops.project.entity.MilestoneStatus.COMPLETED
			order by m.dueDate
			""")
	List<ProjectMilestone> findOpenDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query("select coalesce(max(m.position), 0) from ProjectMilestone m where m.project.id = :projectId")
	int maxPosition(@Param("projectId") Long projectId);

}
