package com.teamops.team.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.teamops.project.dto.ProjectRef;
import com.teamops.sla.service.SlaState;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.user.dto.UserSummary;

/** A team member's work history: their tasks and tickets, with a summary for the same filters. */
public final class WorkHistoryDtos {

	private WorkHistoryDtos() {
	}

	/** Which date the from/to range applies to. For tickets: resolved, resolution due, created (start = created). */
	public enum DateField {

		COMPLETED, DUE, START, CREATED

	}

	/**
	 * A date range relative to the business "today", resolved on the server so the browser's clock never decides it.
	 * Weeks start on Monday; LAST_3_MONTHS and LAST_30_DAYS end today.
	 */
	public enum DatePreset {

		THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, LAST_30_DAYS

	}

	/** The virtual status: open work past its due date (tasks) or SLA due time (tickets). */
	public static final String OVERDUE = "OVERDUE";

	/**
	 * One task. {@code onTime} is set for completed tasks with a due date; {@code daysLate} is how many days a
	 * completed task finished after its due date, or how long an open task has been overdue.
	 */
	public record TaskRow(Long id, String code, String title, ProjectRef project, TaskPriority priority,
			TaskStatus status, LocalDate startDate, LocalDate dueDate, LocalDate completedDate,
			BigDecimal estimatedHours, BigDecimal actualHours, boolean overdue, Boolean onTime, Integer daysLate) {

	}

	/**
	 * One ticket the person is assigned to. {@code dueAt} is the effective resolution due time (paused time added);
	 * {@code onTime} is whether a resolved ticket met its resolution SLA.
	 */
	public record TicketRow(Long id, String code, String subject, String category, TicketPriority priority,
			TicketStatus status, LocalDate createdDate, Instant dueAt, LocalDate resolvedDate, SlaState slaState,
			boolean overdue, Boolean onTime, Integer daysLate) {

	}

	public record WeekCount(LocalDate weekStart, long completed) {

	}

	/**
	 * Figures for the filtered list. Percentages and averages are null when there is nothing to divide by.
	 * @param byStatus count per real status (all statuses listed, zero included)
	 * @param completed tasks completed / tickets resolved
	 * @param completedOnTimePct share of completed work with a due date that finished on time, 1 decimal
	 * @param averageDaysToComplete from start (or creation) to completion, 1 decimal
	 * @param totalActualHours tasks only; null for tickets
	 * @param completedPerWeek completions by business week (Monday) over the range, or the last 12 weeks
	 * @param from the range the filters resolved to (a preset becomes dates), or null when open
	 */
	public record WorkSummary(UserSummary person, LocalDate today, LocalDate from, LocalDate to, long total, Map<String, Long> byStatus, long overdue, long completed,
			BigDecimal completedOnTimePct, BigDecimal averageDaysToComplete, BigDecimal totalActualHours,
			List<WeekCount> completedPerWeek) {

	}

}
