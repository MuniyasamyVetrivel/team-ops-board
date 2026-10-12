package com.teamops.celebration.entity;

/**
 * What an occurrence on the calendar is about. Birthdays and work anniversaries belong to an employee (the subject is
 * the employee), the others to an important day or a marketing event. Keys LinkedIn posts and attachments.
 */
public enum OccasionKind {

	BIRTHDAY, WORK_ANNIVERSARY, IMPORTANT_DAY, EVENT;

	/** Birthdays and anniversaries need no post unless someone says so; important days and events usually do. */
	public boolean postNeededByDefault() {
		return this == IMPORTANT_DAY || this == EVENT;
	}

}
