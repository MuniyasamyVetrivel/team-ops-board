package com.teamops.task.repository;

import java.time.LocalDate;

/** Active, assigned task that is overdue or due soon (projection for the reminder job). */
public interface TaskReminderRow {

	Long getId();

	String getCode();

	String getTitle();

	Long getAssigneeId();

	LocalDate getDueDate();

}
