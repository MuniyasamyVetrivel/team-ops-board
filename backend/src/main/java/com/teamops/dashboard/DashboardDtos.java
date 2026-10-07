package com.teamops.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.teamops.common.security.AccessScope;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.task.dto.TaskListItem;
import com.teamops.task.entity.TaskStatus;
import com.teamops.workload.WorkloadDtos;
import com.teamops.workload.WorkloadLevel;

/** Dashboard API records. Nullable numbers mean "not available" (no data or module not built yet): the UI shows "—". */
public final class DashboardDtos {

	private DashboardDtos() {
	}

	/**
	 * Top KPI row. Ticket, SLA and approval counts stay {@code null} until those modules exist (Phases 7 and 8).
	 * {@code teamMembers} is {@code null} on a personal (OWN) dashboard.
	 */
	public record Kpis(long openTasks, long dueToday, long overdue, long completedThisWeek, long inProgress,
			long blocked, Long teamMembers, Long openTickets, Long slaBreaches, Long pendingApprovals) {

	}

	/** Donut slice. COMPLETED counts only the last {@code completedWindowDays}. */
	public record StatusSlice(TaskStatus status, long count) {

	}

	/**
	 * Department workload and performance. {@code workloadPercent} is the department's remaining hours over its
	 * people's capacity; {@code onTimePercent} is the share of tasks completed in the window (that had a due date)
	 * finished on or before it.
	 */
	public record DepartmentRow(DepartmentSummary department, int people, long openTasks, long overdue,
			long completed, Integer onTimePercent, Integer workloadPercent, WorkloadLevel level) {

	}

	/** The busiest people in scope (or just the viewer on a personal dashboard). */
	public record WorkloadPanel(int people, int averagePercent, Map<WorkloadLevel, Long> byLevel, int windowDays,
			List<WorkloadDtos.Row> rows) {

	}

	/** Tasks completed in the week starting {@code weekStart} (Monday). */
	public record WeekPoint(LocalDate weekStart, long completed, long onTime, Integer onTimePercent) {

	}

	/** One task_history entry. */
	public record ActivityItem(Long id, Instant at, Long actorId, String actorName, Long taskId, String taskCode,
			String taskTitle, String field, String oldValue, String newValue) {

	}

	public record Response(LocalDate today, LocalDate weekStart, AccessScope.Kind scope, Kpis kpis,
			List<StatusSlice> statusDistribution, int completedWindowDays, List<DepartmentRow> departments,
			WorkloadPanel workload, List<WeekPoint> weeklyCompletion, List<TaskListItem> overdueTasks,
			List<TaskListItem> upcomingTasks, List<ActivityItem> recentActivity) {

	}

}
