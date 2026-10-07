package com.teamops.task.entity;

import java.util.EnumSet;
import java.util.Set;

public enum TaskStatus {

	TODO,
	IN_PROGRESS,
	BLOCKED,
	IN_REVIEW,
	COMPLETED,
	CANCELLED;

	/** Work that is still open: counts towards workload, overdue and due-today. */
	public static final Set<TaskStatus> ACTIVE = EnumSet.of(TODO, IN_PROGRESS, BLOCKED, IN_REVIEW);

	public boolean isActive() {
		return ACTIVE.contains(this);
	}

	public boolean isClosed() {
		return this == COMPLETED || this == CANCELLED;
	}

}
