package com.teamops.marketing.activity.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.marketing.activity.entity.Frequency;
import com.teamops.marketing.activity.entity.OccurrenceStatus;
import com.teamops.task.dto.DueState;
import com.teamops.task.dto.TaskRef;
import com.teamops.task.entity.TaskPriority;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Recurring marketing activity API records. Overdue, next due and last completed are computed. */
public final class ActivityDtos {

	private ActivityDtos() {
	}

	/**
	 * One occurrence. {@code dueState} is computed in the business time zone (NONE once closed). {@code task} is the
	 * generated task, when the activity generates tasks. {@code canAct}: the viewer may complete, skip or reopen it
	 * here (only occurrences without a task; the others follow their task).
	 */
	public record OccurrenceItem(Long id, Long activityId, String activityName, Frequency frequency,
			LocalDate periodStart, LocalDate periodEnd, String periodLabel, LocalDate dueDate, DueState dueState,
			OccurrenceStatus status, TaskRef task, UserSummary assignee, Instant completedAt, UserSummary completedBy,
			String notes, Integer version, boolean canAct) {

	}

	/** An activity in the list, with its next open occurrence and last completion. */
	public record ActivityListItem(Long id, String name, Frequency frequency, DepartmentSummary department,
			UserSummary owner, UserSummary assignee, LocalDate startDate, LocalDate endDate, boolean active,
			boolean generatesTasks, Instant lastCompletedAt, OccurrenceItem nextOccurrence, int openCount,
			int overdueCount, Instant updatedAt) {

	}

	public record ActivityPermissions(boolean canEdit) {

	}

	/**
	 * An activity with its checklist template and its 12 most recent occurrences. {@code locked}: it has occurrences,
	 * so frequency and start date are fixed. {@code nextTaskTitle} previews the title of the next generated task.
	 */
	public record ActivityDetail(Long id, String name, String description, Frequency frequency,
			DepartmentSummary department, UserSummary owner, UserSummary defaultAssignee, LocalDate startDate,
			LocalDate endDate, int dueOffsetDays, String taskTitleTemplate, String nextTaskTitle,
			TaskPriority taskPriority, boolean active, List<String> checklist, Instant lastCompletedAt,
			OccurrenceItem nextOccurrence, List<OccurrenceItem> recentOccurrences, long occurrenceCount,
			boolean locked, Integer version, Instant createdAt, Instant updatedAt, ActivityPermissions permissions) {

	}

	public record CreateActivity(
			@NotBlank(message = "Name is required") @Size(max = 200, message = "At most 200 characters") String name,
			@Size(max = 2000, message = "At most 2000 characters") String description,
			@NotNull(message = "Department is required") Long departmentId,
			Long ownerId,
			@NotNull(message = "Frequency is required") Frequency frequency,
			@NotNull(message = "Start date is required") LocalDate startDate,
			LocalDate endDate,
			@NotNull(message = "Due offset is required") @Min(value = 0, message = "0–365") @Max(value = 365, message = "0–365") Integer dueOffsetDays,
			@Size(max = 250, message = "At most 250 characters") String taskTitleTemplate,
			Long defaultAssigneeId,
			TaskPriority taskPriority,
			@Size(max = 30, message = "At most 30 checklist items") List<@NotBlank(message = "Checklist items cannot be empty") @Size(max = 500, message = "At most 500 characters") String> checklist) {

	}

	/** Full replacement. Frequency and start date are fixed once the activity has occurrences. */
	public record UpdateActivity(
			@NotNull(message = "Version is required") Integer version,
			@NotBlank(message = "Name is required") @Size(max = 200, message = "At most 200 characters") String name,
			@Size(max = 2000, message = "At most 2000 characters") String description,
			@NotNull(message = "Department is required") Long departmentId,
			Long ownerId,
			@NotNull(message = "Frequency is required") Frequency frequency,
			@NotNull(message = "Start date is required") LocalDate startDate,
			LocalDate endDate,
			@NotNull(message = "Due offset is required") @Min(value = 0, message = "0–365") @Max(value = 365, message = "0–365") Integer dueOffsetDays,
			@Size(max = 250, message = "At most 250 characters") String taskTitleTemplate,
			Long defaultAssigneeId,
			TaskPriority taskPriority,
			@Size(max = 30, message = "At most 30 checklist items") List<@NotBlank(message = "Checklist items cannot be empty") @Size(max = 500, message = "At most 500 characters") String> checklist,
			@NotNull(message = "Active is required") Boolean active) {

	}

	/** Completing or skipping an occurrence without a task. */
	public record OccurrenceAction(@Size(max = 1000, message = "At most 1000 characters") String notes) {

	}

}
