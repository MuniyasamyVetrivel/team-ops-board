package com.teamops.task.repository;

import java.time.Instant;
import java.time.LocalDate;
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

import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

	/** Paged search; only to-one associations are fetched so paging stays in SQL. */
	@Override
	@EntityGraph(attributePaths = { "department", "project", "assignee" })
	Page<Task> findAll(Specification<Task> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "department", "project", "assignee", "createdBy" })
	Optional<Task> findDetailedById(Long id);

	/** Counts for "My Tasks" in one round trip. */
	@Query("""
			select
			  coalesce(sum(case when t.status in :active then 1 else 0 end), 0) as active,
			  coalesce(sum(case when t.status in :active and t.dueDate < :today then 1 else 0 end), 0) as overdue,
			  coalesce(sum(case when t.status in :active and t.dueDate = :today then 1 else 0 end), 0) as dueToday,
			  coalesce(sum(case when t.status in :active and t.dueDate > :today and t.dueDate <= :soon then 1 else 0 end), 0) as upcoming,
			  coalesce(sum(case when t.status = com.teamops.task.entity.TaskStatus.IN_PROGRESS then 1 else 0 end), 0) as inProgress,
			  coalesce(sum(case when t.status = com.teamops.task.entity.TaskStatus.BLOCKED then 1 else 0 end), 0) as blocked,
			  coalesce(sum(case when t.status = com.teamops.task.entity.TaskStatus.COMPLETED and t.completedAt >= :weekStart then 1 else 0 end), 0) as completedThisWeek
			from Task t
			where t.assignee.id = :userId
			""")
	MyTaskCounts countForAssignee(@Param("userId") Long userId, @Param("active") Collection<TaskStatus> active,
			@Param("today") LocalDate today, @Param("soon") LocalDate soon, @Param("weekStart") Instant weekStart);

	/** Active tasks with an active assignee, due on or before {@code until} (overdue included). */
	@Query("""
			select t.id as id, t.code as code, t.title as title, t.assignee.id as assigneeId, t.dueDate as dueDate
			from Task t
			where t.status in :active and t.dueDate <= :until
			  and t.assignee.status = com.teamops.user.entity.UserStatus.ACTIVE
			order by t.dueDate, t.id
			""")
	List<TaskReminderRow> findReminderCandidates(@Param("active") Collection<TaskStatus> active,
			@Param("until") LocalDate until);

	/** Is {@code from} (transitively) depending on {@code to}? Used to reject dependency cycles. */
	@Query(value = """
			with recursive chain (id) as (
			  select depends_on_task_id from task_dependencies where task_id = :from
			  union
			  select d.depends_on_task_id from task_dependencies d join chain c on d.task_id = c.id
			)
			select count(*) from chain where id = :to
			""", nativeQuery = true)
	long countDependencyPath(@Param("from") Long from, @Param("to") Long to);

}
