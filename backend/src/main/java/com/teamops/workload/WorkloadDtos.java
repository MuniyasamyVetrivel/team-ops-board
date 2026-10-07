package com.teamops.workload;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.user.dto.UserSummary;

/** Workload API records. */
public final class WorkloadDtos {

	private WorkloadDtos() {
	}

	public enum Sort {

		HIGHEST, LOWEST, MOST_OVERDUE, MOST_COMPLETED, MOST_ACTIVE, NAME

	}

	/**
	 * Filters. Status, priority and date range narrow the count columns; workload % always uses the standard
	 * definition so people stay comparable.
	 * @param from with {@code to}: active tasks due in the range, and tasks completed in the range. Without a range,
	 * completed counts the last 30 days.
	 */
	public record Criteria(Long departmentId, Long userId, String search, Set<TaskStatus> statuses,
			Set<TaskPriority> priorities, LocalDate from, LocalDate to, Sort sort) {

		public Criteria {
			statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
			priorities = priorities == null ? Set.of() : Set.copyOf(priorities);
			sort = sort == null ? Sort.HIGHEST : sort;
		}

	}

	public record Row(UserSummary user, DepartmentSummary department, long totalTasks, long todo, long inProgress,
			long blocked, long inReview, long completed, long overdue, long dueToday, long activeTasks,
			BigDecimal remainingHours, BigDecimal capacityHours, int workloadPercent, WorkloadLevel level) {

	}

	public record Summary(int people, Map<WorkloadLevel, Long> byLevel, long activeTasks, long overdue,
			long dueToday, int averagePercent) {

	}

	/** {@code windowDays}, {@code defaultTaskHours} and {@code today} explain how percentages were computed. */
	public record Response(LocalDate today, int windowDays, BigDecimal defaultTaskHours, LocalDate from,
			LocalDate to, Summary summary, List<Row> rows) {

	}

}
