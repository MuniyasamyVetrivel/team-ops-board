package com.teamops.dashboard;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.common.security.AccessScope;
import com.teamops.dashboard.DashboardDtos.ActivityItem;
import com.teamops.dashboard.DashboardMath.WeekCounts;
import com.teamops.task.entity.TaskStatus;

import lombok.RequiredArgsConstructor;

/**
 * The dashboard's aggregate queries (brief section 70: a handful of queries, no per-row lookups). Every query is
 * restricted to the viewer's work: all tasks (ALL), managed departments plus own assignments (DEPARTMENTS), or own
 * assignments (OWN), the same rule as {@code TaskSpecifications.workOf}.
 * <p>
 * "On time" compares the completion time, shifted into the business zone by {@code zoneOffsetSeconds}, with the
 * due date. The offset is today's offset for the zone, which is exact for zones without daylight saving (such as
 * the default Asia/Kolkata).
 */
@Repository
@RequiredArgsConstructor
public class DashboardQuery {

	private static final List<String> ACTIVE = TaskStatus.ACTIVE.stream().map(Enum::name).toList();

	private final NamedParameterJdbcTemplate jdbc;

	public record TaskCounts(long open, long dueToday, long overdue, long completedThisWeek, long todo,
			long inProgress, long blocked, long inReview, long completedRecently) {

	}

	public record DepartmentCounts(long open, long overdue, long completed, long completedWithDueDate,
			long completedOnTime) {

		public static final DepartmentCounts EMPTY = new DepartmentCounts(0, 0, 0, 0, 0);

	}

	/** KPI and status-distribution counts in one pass. */
	public TaskCounts taskCounts(AccessScope scope, LocalDate today, Instant weekStart, Instant recentSince) {
		MapSqlParameterSource params = params(scope).addValue("today", Date.valueOf(today))
			.addValue("weekStart", Timestamp.from(weekStart))
			.addValue("recentSince", Timestamp.from(recentSince));
		String sql = """
				select
				  coalesce(sum(case when t.status in (:active) then 1 else 0 end), 0) as open_tasks,
				  coalesce(sum(case when t.status in (:active) and t.due_date = :today then 1 else 0 end), 0) as due_today,
				  coalesce(sum(case when t.status in (:active) and t.due_date < :today then 1 else 0 end), 0) as overdue,
				  coalesce(sum(case when t.status = 'COMPLETED' and t.completed_at >= :weekStart then 1 else 0 end), 0) as completed_week,
				  coalesce(sum(case when t.status = 'TODO' then 1 else 0 end), 0) as todo,
				  coalesce(sum(case when t.status = 'IN_PROGRESS' then 1 else 0 end), 0) as in_progress,
				  coalesce(sum(case when t.status = 'BLOCKED' then 1 else 0 end), 0) as blocked,
				  coalesce(sum(case when t.status = 'IN_REVIEW' then 1 else 0 end), 0) as in_review,
				  coalesce(sum(case when t.status = 'COMPLETED' and t.completed_at >= :recentSince then 1 else 0 end), 0) as completed_recent
				from tasks t
				where %s
				""".formatted(scopeClause(scope));
		return jdbc.queryForObject(sql, params,
				(rs, i) -> new TaskCounts(rs.getLong("open_tasks"), rs.getLong("due_today"), rs.getLong("overdue"),
						rs.getLong("completed_week"), rs.getLong("todo"), rs.getLong("in_progress"),
						rs.getLong("blocked"), rs.getLong("in_review"), rs.getLong("completed_recent")));
	}

	/** Per-department open, overdue and recently completed counts for the given departments. */
	public Map<Long, DepartmentCounts> departmentCounts(Collection<Long> departmentIds, LocalDate today,
			Instant completedSince, int zoneOffsetSeconds) {
		if (departmentIds.isEmpty()) {
			return Map.of();
		}
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("active", ACTIVE)
			.addValue("departmentIds", departmentIds)
			.addValue("today", Date.valueOf(today))
			.addValue("since", Timestamp.from(completedSince))
			.addValue("offset", zoneOffsetSeconds);
		String sql = """
				select t.department_id,
				  sum(case when t.status in (:active) then 1 else 0 end) as open_tasks,
				  sum(case when t.status in (:active) and t.due_date < :today then 1 else 0 end) as overdue,
				  sum(case when t.status = 'COMPLETED' and t.completed_at >= :since then 1 else 0 end) as completed,
				  sum(case when t.status = 'COMPLETED' and t.completed_at >= :since and t.due_date is not null
				      then 1 else 0 end) as completed_with_due,
				  sum(case when t.status = 'COMPLETED' and t.completed_at >= :since and t.due_date is not null
				        and date(timestampadd(second, :offset, t.completed_at)) <= t.due_date
				      then 1 else 0 end) as on_time
				from tasks t
				where t.department_id in (:departmentIds)
				group by t.department_id
				""";
		Map<Long, DepartmentCounts> result = new HashMap<>();
		jdbc.query(sql, params,
				rs -> {
					result.put(rs.getLong("department_id"),
							new DepartmentCounts(rs.getLong("open_tasks"), rs.getLong("overdue"),
									rs.getLong("completed"), rs.getLong("completed_with_due"), rs.getLong("on_time")));
				});
		return result;
	}

	/** Completions per business-zone week (Monday start) since {@code since}. */
	public Map<LocalDate, WeekCounts> weeklyCompletions(AccessScope scope, Instant since, int zoneOffsetSeconds) {
		MapSqlParameterSource params = params(scope).addValue("since", Timestamp.from(since))
			.addValue("offset", zoneOffsetSeconds);
		String sql = """
				select date_sub(x.local_day, interval weekday(x.local_day) day) as week_start,
				  count(*) as completed,
				  sum(case when x.due_date is not null then 1 else 0 end) as with_due,
				  sum(case when x.due_date is not null and x.local_day <= x.due_date then 1 else 0 end) as on_time
				from (
				  select date(timestampadd(second, :offset, t.completed_at)) as local_day, t.due_date
				  from tasks t
				  where t.status = 'COMPLETED' and t.completed_at >= :since and %s
				) x
				group by week_start
				""".formatted(scopeClause(scope));
		Map<LocalDate, WeekCounts> result = new HashMap<>();
		jdbc.query(sql, params, rs -> {
			result.put(rs.getDate("week_start").toLocalDate(),
					new WeekCounts(rs.getLong("completed"), rs.getLong("with_due"), rs.getLong("on_time")));
		});
		return result;
	}

	/** Latest task changes within scope. */
	public List<ActivityItem> recentActivity(AccessScope scope, int limit) {
		MapSqlParameterSource params = params(scope).addValue("limit", limit);
		String sql = """
				select h.id, h.changed_at, h.field_name, h.old_value, h.new_value,
				  t.id as task_id, t.code, t.title,
				  u.id as actor_id, trim(concat(u.first_name, ' ', coalesce(u.last_name, ''))) as actor_name
				from task_history h
				join tasks t on t.id = h.task_id
				left join users u on u.id = h.changed_by
				where %s
				order by h.changed_at desc, h.id desc
				limit :limit
				""".formatted(scopeClause(scope));
		return jdbc.query(sql, params, (rs, i) -> {
			long actorId = rs.getLong("actor_id");
			return new ActivityItem(rs.getLong("id"), rs.getTimestamp("changed_at").toInstant(),
					rs.wasNull() ? null : actorId, rs.getString("actor_name"), rs.getLong("task_id"),
					rs.getString("code"), rs.getString("title"), rs.getString("field_name"),
					rs.getString("old_value"), rs.getString("new_value"));
		});
	}

	private static MapSqlParameterSource params(AccessScope scope) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("active", ACTIVE)
			.addValue("me", scope.userId());
		if (scope.kind() == AccessScope.Kind.DEPARTMENTS && !scope.departmentIds().isEmpty()) {
			params.addValue("scopeDepartments", scope.departmentIds());
		}
		return params;
	}

	/** SQL condition on alias {@code t} matching {@code TaskSpecifications.workOf}. */
	static String scopeClause(AccessScope scope) {
		return switch (scope.kind()) {
			case ALL -> "1 = 1";
			case DEPARTMENTS -> scope.departmentIds().isEmpty() ? "t.assignee_id = :me"
					: "(t.department_id in (:scopeDepartments) or t.assignee_id = :me)";
			case OWN -> "t.assignee_id = :me";
		};
	}

}
