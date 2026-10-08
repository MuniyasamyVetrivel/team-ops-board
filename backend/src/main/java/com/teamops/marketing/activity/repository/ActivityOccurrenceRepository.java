package com.teamops.marketing.activity.repository;

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

import com.teamops.marketing.activity.entity.ActivityOccurrence;
import com.teamops.marketing.activity.entity.OccurrenceStatus;

public interface ActivityOccurrenceRepository
		extends JpaRepository<ActivityOccurrence, Long>, JpaSpecificationExecutor<ActivityOccurrence> {

	@Override
	@EntityGraph(attributePaths = { "activity", "activity.owner", "activity.defaultAssignee", "task", "task.assignee",
			"completedBy" })
	Page<ActivityOccurrence> findAll(Specification<ActivityOccurrence> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "activity", "activity.owner", "activity.defaultAssignee", "task", "task.assignee",
			"completedBy" })
	Optional<ActivityOccurrence> findDetailedById(Long id);

	@EntityGraph(attributePaths = { "activity", "activity.owner", "activity.defaultAssignee", "task" })
	Optional<ActivityOccurrence> findByTaskId(Long taskId);

	Optional<ActivityOccurrence> findByActivityIdAndPeriodStart(Long activityId, LocalDate periodStart);

	boolean existsByActivityId(Long activityId);

	long countByActivityId(Long activityId);

	/** The most recent occurrences of an activity, newest period first. */
	@EntityGraph(attributePaths = { "task", "task.assignee", "completedBy" })
	List<ActivityOccurrence> findTop12ByActivityIdOrderByPeriodStartDesc(Long activityId);

	/** Open occurrences of these activities, earliest due first (next due and overdue counts). */
	@Query("select o from ActivityOccurrence o join fetch o.activity where o.activity.id in :ids "
			+ "and o.status in :open order by o.dueDate, o.id")
	List<ActivityOccurrence> findOpen(@Param("ids") Collection<Long> activityIds,
			@Param("open") Collection<OccurrenceStatus> open);

	/** Last completion per activity. */
	@Query("select o.activity.id as activityId, max(o.completedAt) as completedAt from ActivityOccurrence o "
			+ "where o.activity.id in :ids and o.status = com.teamops.marketing.activity.entity.OccurrenceStatus.COMPLETED "
			+ "group by o.activity.id")
	List<LastCompleted> findLastCompleted(@Param("ids") Collection<Long> activityIds);

	/** Open occurrences without a task that are due by {@code until} (their reminders; tasks have their own). */
	@Query("select o from ActivityOccurrence o join fetch o.activity a left join fetch a.owner "
			+ "left join fetch a.defaultAssignee where o.task is null and o.status in :open and o.dueDate <= :until")
	List<ActivityOccurrence> findTasklessDue(@Param("open") Collection<OccurrenceStatus> open,
			@Param("until") LocalDate until);

	interface LastCompleted {

		Long getActivityId();

		Instant getCompletedAt();

	}

}
