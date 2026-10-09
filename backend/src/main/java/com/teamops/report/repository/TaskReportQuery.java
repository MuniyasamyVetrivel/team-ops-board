package com.teamops.report.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.common.security.AccessScope;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.repository.TaskWorkScope;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.UserStatus;

import lombok.RequiredArgsConstructor;

/**
 * The task report from grouped queries over the viewer's work ({@link TaskWorkScope}): one pass of sums, the same
 * sums per department and per assignee, the open work by status, and created/completed per business week. "On time"
 * compares the completion day in the business zone ({@code offsetSeconds}) with the due date, like the dashboard.
 */
@Repository
@RequiredArgsConstructor
public class TaskReportQuery {

	/**
	 * The report's tasks: the viewer's scope narrowed by department, assignee, project and current status.
	 * {@code from}/{@code to} bound creation and completion (to is exclusive); open and overdue are as of {@code today}.
	 */
	public record Filter(AccessScope scope, Instant from, Instant to, LocalDate today, int offsetSeconds, Long departmentId,
			Long assigneeId, Long projectId, Set<TaskStatus> statuses) {
	}

	public record Sums(long created, long completed, long completedWithDue, long completedOnTime, long open,
			long overdue, BigDecimal hoursLogged) {

		public static final Sums ZERO = new Sums(0, 0, 0, 0, 0, 0, BigDecimal.ZERO);

	}

	public record DepartmentSums(DepartmentSummary department, Sums sums) {
	}

	public record AssigneeSums(UserSummary user, DepartmentSummary department, Sums sums) {
	}

	public record Week(LocalDate weekStart, long created, long completed) {
	}

	private static final String COMPLETED_IN_RANGE = "t.status = 'COMPLETED' and t.completed_at >= :from and t.completed_at < :to";

	private static final String SUMS = """
			coalesce(sum(case when t.created_at >= :from and t.created_at < :to then 1 else 0 end), 0) as created,
			coalesce(sum(case when %1$s then 1 else 0 end), 0) as completed,
			coalesce(sum(case when %1$s and t.due_date is not null then 1 else 0 end), 0) as completed_with_due,
			coalesce(sum(case when %1$s and t.due_date is not null
			    and date(timestampadd(second, :offset, t.completed_at)) <= t.due_date then 1 else 0 end), 0) as on_time,
			coalesce(sum(case when t.status in (:active) then 1 else 0 end), 0) as open_tasks,
			coalesce(sum(case when t.status in (:active) and t.due_date < :today then 1 else 0 end), 0) as overdue,
			coalesce(sum(case when %1$s then coalesce(t.actual_hours, 0) else 0 end), 0) as hours
			""".formatted(COMPLETED_IN_RANGE);

	private final NamedParameterJdbcTemplate jdbc;

	public Sums summary(Filter f) {
		return jdbc.queryForObject("select " + SUMS + " from tasks t where " + where(f), params(f), (rs, i) -> sums(rs));
	}

	/** Departments with any task in the report, by name. */
	public List<DepartmentSums> byDepartment(Filter f) {
		List<DepartmentSums> rows = new ArrayList<>();
		jdbc.query("select d.id as d_id, d.name as d_name, d.code as d_code, " + SUMS
				+ " from tasks t join departments d on d.id = t.department_id where " + where(f)
				+ " group by d.id, d.name, d.code order by d.name", params(f), rs -> {
					rows.add(new DepartmentSums(new DepartmentSummary(rs.getLong("d_id"), rs.getString("d_name"),
							rs.getString("d_code")), sums(rs)));
				});
		return rows;
	}

	/** Assignees with any task in the report, most completed first. */
	public List<AssigneeSums> byAssignee(Filter f) {
		List<AssigneeSums> rows = new ArrayList<>();
		jdbc.query("""
				select u.id as u_id, u.first_name, u.last_name, u.email, u.job_title, u.status as u_status,
				  d.id as d_id, d.name as d_name, d.code as d_code, %s
				from tasks t join users u on u.id = t.assignee_id join departments d on d.id = u.department_id
				where %s
				group by u.id, u.first_name, u.last_name, u.email, u.job_title, u.status, d.id, d.name, d.code
				order by completed desc, u.first_name, u.last_name
				""".formatted(SUMS, where(f)), params(f), rs -> {
			String name = (rs.getString("first_name") + " " + (rs.getString("last_name") == null ? "" : rs.getString("last_name"))).trim();
			rows.add(new AssigneeSums(new UserSummary(rs.getLong("u_id"), name, rs.getString("email"), rs.getString("job_title"),
					UserStatus.valueOf(rs.getString("u_status"))),
					new DepartmentSummary(rs.getLong("d_id"), rs.getString("d_name"), rs.getString("d_code")), sums(rs)));
		});
		return rows;
	}

	/** Open tasks now, by status. */
	public Map<TaskStatus, Long> openByStatus(Filter f) {
		Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
		jdbc.query("select t.status, count(*) as n from tasks t where t.status in (:active) and " + where(f)
				+ " group by t.status", params(f), rs -> {
					counts.put(TaskStatus.valueOf(rs.getString("status")), rs.getLong("n"));
				});
		return counts;
	}

	/** Tasks created and completed per business week (Monday start) within the range. */
	public Map<LocalDate, Week> weekly(Filter f) {
		String sql = """
				select date_sub(x.local_day, interval weekday(x.local_day) day) as week_start,
				  sum(x.created) as created, sum(x.completed) as completed
				from (
				  select date(timestampadd(second, :offset, t.created_at)) as local_day, 1 as created, 0 as completed
				  from tasks t where t.created_at >= :from and t.created_at < :to and %1$s
				  union all
				  select date(timestampadd(second, :offset, t.completed_at)), 0, 1
				  from tasks t where %2$s and %1$s
				) x
				group by week_start
				""".formatted(where(f), COMPLETED_IN_RANGE);
		Map<LocalDate, Week> weeks = new HashMap<>();
		jdbc.query(sql, params(f), rs -> {
			LocalDate week = rs.getDate("week_start").toLocalDate();
			weeks.put(week, new Week(week, rs.getLong("created"), rs.getLong("completed")));
		});
		return weeks;
	}

	private static Sums sums(ResultSet rs) throws SQLException {
		return new Sums(rs.getLong("created"), rs.getLong("completed"), rs.getLong("completed_with_due"),
				rs.getLong("on_time"), rs.getLong("open_tasks"), rs.getLong("overdue"), rs.getBigDecimal("hours"));
	}

	private static String where(Filter f) {
		StringBuilder where = new StringBuilder(TaskWorkScope.clause(f.scope()));
		if (f.departmentId() != null) {
			where.append(" and t.department_id = :departmentId");
		}
		if (f.assigneeId() != null) {
			where.append(" and t.assignee_id = :assigneeId");
		}
		if (f.projectId() != null) {
			where.append(" and t.project_id = :projectId");
		}
		if (f.statuses() != null && !f.statuses().isEmpty()) {
			where.append(" and t.status in (:statuses)");
		}
		return where.toString();
	}

	private static MapSqlParameterSource params(Filter f) {
		MapSqlParameterSource params = TaskWorkScope.params(f.scope())
			.addValue("from", Timestamp.from(f.from()))
			.addValue("to", Timestamp.from(f.to()))
			.addValue("today", Date.valueOf(f.today()))
			.addValue("offset", f.offsetSeconds());
		if (f.departmentId() != null) {
			params.addValue("departmentId", f.departmentId());
		}
		if (f.assigneeId() != null) {
			params.addValue("assigneeId", f.assigneeId());
		}
		if (f.projectId() != null) {
			params.addValue("projectId", f.projectId());
		}
		if (f.statuses() != null && !f.statuses().isEmpty()) {
			params.addValue("statuses", f.statuses().stream().map(Enum::name).toList());
		}
		return params;
	}

}
