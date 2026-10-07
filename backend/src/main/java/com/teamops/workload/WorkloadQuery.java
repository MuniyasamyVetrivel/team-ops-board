package com.teamops.workload;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;

import lombok.RequiredArgsConstructor;

/**
 * One aggregate query per request for all people at once (brief section 70: no per-user queries). Native SQL is
 * used for conditional sums and GREATEST, which JPQL cannot express portably.
 */
@Repository
@RequiredArgsConstructor
public class WorkloadQuery {

	private static final List<String> ACTIVE = TaskStatus.ACTIVE.stream().map(Enum::name).toList();

	private final NamedParameterJdbcTemplate jdbc;

	/** Per-assignee counts and remaining hours. */
	public record Counts(long todo, long inProgress, long blocked, long inReview, long completed, long overdue,
			long dueToday, long active, BigDecimal remainingHours) {

		static final Counts EMPTY = new Counts(0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO);

		public long total() {
			return active + completed;
		}

	}

	/**
	 * @param from/to optional range: active tasks due in it, tasks completed in it
	 * @param completedSince used when no range is given
	 */
	public Map<Long, Counts> counts(Collection<Long> userIds, LocalDate today, LocalDate windowEnd,
			BigDecimal defaultHours, Set<TaskStatus> statuses, Set<TaskPriority> priorities, LocalDate from,
			LocalDate to, Instant rangeStart, Instant rangeEnd, Instant completedSince) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("userIds", userIds)
			.addValue("active", ACTIVE)
			.addValue("today", Date.valueOf(today))
			.addValue("windowEnd", Date.valueOf(windowEnd))
			.addValue("defaultHours", defaultHours);

		StringBuilder countFilter = new StringBuilder();
		if (!statuses.isEmpty()) {
			countFilter.append(" and t.status in (:statuses)");
			params.addValue("statuses", statuses.stream().map(Enum::name).toList());
		}
		if (!priorities.isEmpty()) {
			countFilter.append(" and t.priority in (:priorities)");
			params.addValue("priorities", priorities.stream().map(Enum::name).toList());
		}
		String activeInRange;
		String completedInRange;
		if (from != null && to != null) {
			activeInRange = "t.status in (:active) and t.due_date between :from and :to";
			completedInRange = "t.status = 'COMPLETED' and t.completed_at >= :rangeStart and t.completed_at < :rangeEnd";
			params.addValue("from", Date.valueOf(from))
				.addValue("to", Date.valueOf(to))
				.addValue("rangeStart", Timestamp.from(rangeStart))
				.addValue("rangeEnd", Timestamp.from(rangeEnd));
		}
		else {
			activeInRange = "t.status in (:active)";
			completedInRange = "t.status = 'COMPLETED' and t.completed_at >= :completedSince";
			params.addValue("completedSince", Timestamp.from(completedSince));
		}

		String sql = """
				select t.assignee_id as user_id,
				  sum(case when %1$s and t.status = 'TODO' %3$s then 1 else 0 end) as todo,
				  sum(case when %1$s and t.status = 'IN_PROGRESS' %3$s then 1 else 0 end) as in_progress,
				  sum(case when %1$s and t.status = 'BLOCKED' %3$s then 1 else 0 end) as blocked,
				  sum(case when %1$s and t.status = 'IN_REVIEW' %3$s then 1 else 0 end) as in_review,
				  sum(case when %2$s %3$s then 1 else 0 end) as completed,
				  sum(case when %1$s and t.due_date < :today %3$s then 1 else 0 end) as overdue,
				  sum(case when %1$s and t.due_date = :today %3$s then 1 else 0 end) as due_today,
				  sum(case when %1$s %3$s then 1 else 0 end) as active,
				  sum(case when t.status in (:active) and (t.due_date is null or t.due_date <= :windowEnd)
				        then case when t.estimated_hours is null then :defaultHours
				                  else greatest(t.estimated_hours - coalesce(t.actual_hours, 0), 0) end
				        else 0 end) as remaining_hours
				from tasks t
				where t.assignee_id in (:userIds)
				group by t.assignee_id
				""".formatted(activeInRange, completedInRange, countFilter);

		Map<Long, Counts> result = new HashMap<>();
		jdbc.query(sql, params, rs -> {
			BigDecimal remaining = rs.getBigDecimal("remaining_hours");
			result.put(rs.getLong("user_id"),
					new Counts(rs.getLong("todo"), rs.getLong("in_progress"), rs.getLong("blocked"),
							rs.getLong("in_review"), rs.getLong("completed"), rs.getLong("overdue"),
							rs.getLong("due_today"), rs.getLong("active"),
							remaining == null ? BigDecimal.ZERO : remaining));
		});
		return result;
	}

}
