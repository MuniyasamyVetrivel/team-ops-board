package com.teamops.marketing.activity.entity;

/**
 * Lifecycle of one occurrence. Overdue is not a status: it is computed from the due date. An occurrence with a task
 * follows the task (in progress, completed; a cancelled task skips the occurrence).
 */
public enum OccurrenceStatus {

	PENDING, IN_PROGRESS, COMPLETED, SKIPPED;

	public boolean isOpen() {
		return this == PENDING || this == IN_PROGRESS;
	}

}
