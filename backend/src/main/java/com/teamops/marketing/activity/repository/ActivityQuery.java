package com.teamops.marketing.activity.repository;

import java.time.LocalDate;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.marketing.common.MarketingPeriod;

import lombok.RequiredArgsConstructor;

/** Recurring activity occurrences counted in one grouped query (the marketing dashboard). */
@Repository
@RequiredArgsConstructor
public class ActivityQuery {

	/**
	 * Occurrences due in a month: completed, skipped, still open, and open past their due date (overdue, as of
	 * today).
	 */
	public record MonthCounts(long due, long completed, long skipped, long open, long overdue) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	/**
	 * The month's occurrences, optionally for one person: the generated task's assignee, else the activity's default
	 * assignee, else its owner (the same rule as the occurrence list).
	 */
	public MonthCounts month(MarketingPeriod period, Long personId, LocalDate today) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", period.firstDay())
			.addValue("to", period.lastDay())
			.addValue("today", today);
		String person = "";
		if (personId != null) {
			person = " and (t.assignee_id = :personId or (t.id is null and (a.default_assignee_id = :personId"
					+ " or (a.default_assignee_id is null and a.owner_id = :personId))))";
			params.addValue("personId", personId);
		}
		return jdbc.queryForObject("""
				select count(*) as due,
				  coalesce(sum(case when o.status = 'COMPLETED' then 1 else 0 end), 0) as completed,
				  coalesce(sum(case when o.status = 'SKIPPED' then 1 else 0 end), 0) as skipped,
				  coalesce(sum(case when o.status in ('PENDING', 'IN_PROGRESS') then 1 else 0 end), 0) as open_count,
				  coalesce(sum(case when o.status in ('PENDING', 'IN_PROGRESS') and o.due_date < :today then 1 else 0 end), 0) as overdue
				from marketing_activity_occurrences o
				join marketing_activities a on a.id = o.activity_id
				left join tasks t on t.id = o.task_id
				where o.due_date between :from and :to%s
				""".formatted(person), params,
				(rs, row) -> new MonthCounts(rs.getLong("due"), rs.getLong("completed"), rs.getLong("skipped"),
						rs.getLong("open_count"), rs.getLong("overdue")));
	}

}
