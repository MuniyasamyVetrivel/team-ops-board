package com.teamops.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.project.entity.ProjectStatus;
import com.teamops.task.entity.TaskStatus;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.user.dto.UserSummary;
import com.teamops.workload.WorkloadDtos;
import com.teamops.workload.WorkloadLevel;

/**
 * Management reports (brief sections 20 and 78). Every figure is computed per request within the viewer's scope;
 * percentages are null when there is nothing to divide by ("—").
 */
public final class ReportDtos {

	private ReportDtos() {
	}

	/** The business-day range a report covers, both ends included. */
	public record Range(LocalDate from, LocalDate to) {

	}

	/**
	 * Tasks created and completed in the range, and the open work now. {@code onTimePct} = completed on or before the
	 * due date ÷ completed with a due date; {@code overduePct} = overdue ÷ open.
	 */
	public record TaskSummary(long created, long completed, long completedWithDueDate, long completedOnTime,
			Integer onTimePct, long open, long overdue, Integer overduePct, BigDecimal hoursLogged) {

	}

	public record StatusCount(TaskStatus status, long tasks) {

	}

	public record TaskDepartmentRow(DepartmentSummary department, long created, long completed, Integer onTimePct,
			long open, long overdue, Integer overduePct) {

	}

	/** Employee productivity: what each assignee completed in the range, on time, and what is open now. */
	public record EmployeeRow(UserSummary user, DepartmentSummary department, long completed, Integer onTimePct,
			long open, long overdue, BigDecimal hoursLogged) {

	}

	public record WeekRow(LocalDate weekStart, long created, long completed) {

	}

	public record TaskReport(Range range, TaskSummary summary, List<StatusCount> openByStatus,
			List<TaskDepartmentRow> departments, List<EmployeeRow> employees, List<WeekRow> trend) {

	}

	/** A department's workload: its people's remaining hours ÷ their capacity in the window. */
	public record DepartmentWorkload(DepartmentSummary department, int people, long activeTasks, long overdue,
			BigDecimal remainingHours, BigDecimal capacityHours, Integer workloadPercent, WorkloadLevel level) {

	}

	public record WorkloadReport(WorkloadDtos.Response employees, List<DepartmentWorkload> departments) {

	}

	/**
	 * Tickets created and resolved in the range, compliance with the SLA of those created in it, and the open
	 * tickets now (with their age and breaches).
	 */
	public record TicketSummary(long created, long resolved, long open, long openBreached,
			Integer firstResponseCompliance, Integer resolutionCompliance, BigDecimal averageResolutionHours) {

	}

	public record TicketPriorityRow(TicketPriority priority, long created, long resolved, long open,
			Integer firstResponseCompliance, Integer resolutionCompliance) {

	}

	public record TicketDepartmentRow(DepartmentSummary department, long created, long resolved, long open,
			long openBreached, Integer resolutionCompliance) {

	}

	/** Open tickets by age since creation, e.g. "1–3 days". */
	public record AgeBucket(String label, long tickets) {

	}

	public record TicketStatusCount(TicketStatus status, long tickets) {

	}

	public record TicketReport(Range range, TicketSummary summary, List<TicketPriorityRow> priorities,
			List<TicketDepartmentRow> departments, List<AgeBucket> ageing, List<TicketStatusCount> openByStatus) {

	}

	/** A project's progress (computed, or the manual override), tasks, milestones and risks. */
	public record ProjectRow(Long id, String code, String name, ProjectStatus status, DepartmentSummary department,
			UserSummary owner, LocalDate startDate, LocalDate endDate, Integer progress, long tasks,
			long tasksCompleted, long tasksOverdue, long milestones, long milestonesCompleted, long milestonesOverdue,
			long openRisks, boolean pastEndDate) {

	}

	public record ProjectStatusCount(ProjectStatus status, long projects) {

	}

	public record ProjectReport(LocalDate today, List<ProjectStatusCount> byStatus, List<ProjectRow> projects) {

	}

}
