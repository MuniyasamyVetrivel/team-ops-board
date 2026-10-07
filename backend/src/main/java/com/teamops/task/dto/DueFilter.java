package com.teamops.task.dto;

/** Due-date views over active tasks, evaluated against "today" in the business time zone. */
public enum DueFilter {

	/** Due before today. */
	OVERDUE,
	/** Due today. */
	TODAY,
	/** Due in the next 7 days, excluding today. */
	UPCOMING,
	/** No due date set. */
	NO_DUE_DATE

}
