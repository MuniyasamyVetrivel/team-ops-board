package com.teamops.calendar.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.calendar.entity.CalendarEventType;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.task.dto.DueState;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Calendar API records. */
public final class CalendarDtos {

	private CalendarDtos() {
	}

	public enum ItemKind {

		EVENT, TASK_DEADLINE, MILESTONE, APPROVAL_DUE

	}

	/** Task fields shown on a deadline item. */
	public record TaskInfo(String code, TaskStatus status, TaskPriority priority, DueState dueState) {

	}

	/**
	 * One entry on the calendar. {@code startDate}/{@code endDate} (inclusive) are business-zone dates for every
	 * item; {@code startAt}/{@code endAt} are set for timed events only.
	 * @param key unique across kinds, e.g. "EVENT-7", "TASK-42", "MILESTONE-3" or "APPROVAL-9"
	 * @param reference code of the related record (project code for milestones, approval code), or null
	 * @param parentId the project for milestones, otherwise null
	 */
	public record CalendarItem(String key, ItemKind kind, Long id, String title, CalendarEventType eventType,
			boolean allDay, LocalDate startDate, LocalDate endDate, Instant startAt, Instant endAt,
			DepartmentSummary department, UserSummary user, TaskInfo task, String reference, Long parentId) {

	}

	public record CalendarResponse(LocalDate today, LocalDate from, LocalDate to, List<CalendarItem> items) {

	}

	public record EventDetail(Long id, String title, String description, CalendarEventType eventType,
			boolean allDay, LocalDate startDate, LocalDate endDate, Instant startAt, Instant endAt,
			DepartmentSummary department, UserSummary user, UserSummary createdBy, Integer version,
			boolean canEdit) {

	}

	/**
	 * Create or update. All-day events use {@code startDate}/{@code endDate}; timed events use
	 * {@code startAt}/{@code endAt}. LEAVE needs {@code userId}, and the event is filed under that person's department.
	 * No department and no user means company-wide (Super Admin only). {@code version} is required on update.
	 */
	public record SaveEvent(@NotBlank @Size(max = 200) String title, @Size(max = 5000) String description,
			@NotNull CalendarEventType eventType, boolean allDay, LocalDate startDate, LocalDate endDate,
			Instant startAt, Instant endAt, Long departmentId, Long userId, Integer version) {

	}

}
