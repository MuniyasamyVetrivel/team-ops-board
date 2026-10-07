package com.teamops.task.dto;

import java.time.LocalDate;

import com.teamops.task.entity.TaskStatus;

/**
 * How urgent a task's due date is, computed by the server in the business time zone so every client agrees.
 * Closed tasks are always {@link #NONE}.
 */
public enum DueState {

	OVERDUE, DUE_TODAY, DUE_SOON, SCHEDULED, NONE;

	static final int SOON_DAYS = 3;

	public static DueState of(LocalDate dueDate, TaskStatus status, LocalDate today) {
		if (dueDate == null || !status.isActive()) {
			return NONE;
		}
		if (dueDate.isBefore(today)) {
			return OVERDUE;
		}
		if (dueDate.isEqual(today)) {
			return DUE_TODAY;
		}
		return dueDate.isAfter(today.plusDays(SOON_DAYS)) ? SCHEDULED : DUE_SOON;
	}

}
