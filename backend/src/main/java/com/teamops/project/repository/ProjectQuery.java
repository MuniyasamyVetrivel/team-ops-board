package com.teamops.project.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Per-project aggregates for a page of projects: three grouped queries, never one per project. */
@Repository
@RequiredArgsConstructor
public class ProjectQuery {

	private final NamedParameterJdbcTemplate jdbc;

	/** Cancelled tasks do not count towards progress. */
	public record TaskCounts(long total, long completed, long open, long overdue) {

		public static final TaskCounts EMPTY = new TaskCounts(0, 0, 0, 0);

	}

	public record MilestoneCounts(long total, long completed, long overdue) {

		public static final MilestoneCounts EMPTY = new MilestoneCounts(0, 0, 0);

	}

	public record Stats(Map<Long, TaskCounts> tasks, Map<Long, MilestoneCounts> milestones, Map<Long, Long> openRisks) {

		public TaskCounts tasksOf(Long id) {
			return tasks.getOrDefault(id, TaskCounts.EMPTY);
		}

		public MilestoneCounts milestonesOf(Long id) {
			return milestones.getOrDefault(id, MilestoneCounts.EMPTY);
		}

		public long openRisksOf(Long id) {
			return openRisks.getOrDefault(id, 0L);
		}

	}

	public Stats stats(Collection<Long> projectIds, LocalDate today) {
		if (projectIds.isEmpty()) {
			return new Stats(Map.of(), Map.of(), Map.of());
		}
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", projectIds)
			.addValue("today", Date.valueOf(today));
		Map<Long, TaskCounts> tasks = new HashMap<>();
		jdbc.query("""
				select t.project_id,
				  sum(case when t.status <> 'CANCELLED' then 1 else 0 end) as total,
				  sum(case when t.status = 'COMPLETED' then 1 else 0 end) as completed,
				  sum(case when t.status in ('TODO', 'IN_PROGRESS', 'BLOCKED', 'IN_REVIEW') then 1 else 0 end) as open_tasks,
				  sum(case when t.status in ('TODO', 'IN_PROGRESS', 'BLOCKED', 'IN_REVIEW') and t.due_date < :today
				      then 1 else 0 end) as overdue
				from tasks t where t.project_id in (:ids) group by t.project_id
				""", params, rs -> {
			tasks.put(rs.getLong("project_id"), new TaskCounts(rs.getLong("total"), rs.getLong("completed"),
					rs.getLong("open_tasks"), rs.getLong("overdue")));
		});
		Map<Long, MilestoneCounts> milestones = new HashMap<>();
		jdbc.query("""
				select m.project_id, count(*) as total,
				  sum(case when m.status = 'COMPLETED' then 1 else 0 end) as completed,
				  sum(case when m.status <> 'COMPLETED' and m.due_date < :today then 1 else 0 end) as overdue
				from project_milestones m where m.project_id in (:ids) group by m.project_id
				""", params, rs -> {
			milestones.put(rs.getLong("project_id"),
					new MilestoneCounts(rs.getLong("total"), rs.getLong("completed"), rs.getLong("overdue")));
		});
		Map<Long, Long> risks = new HashMap<>();
		jdbc.query("""
				select r.project_id, count(*) as open_risks from project_risks r
				where r.project_id in (:ids) and r.status = 'OPEN' group by r.project_id
				""", params, rs -> {
			risks.put(rs.getLong("project_id"), rs.getLong("open_risks"));
		});
		return new Stats(tasks, milestones, risks);
	}

}
