package com.teamops.task.repository;

/** Aggregate projection for the "My Tasks" summary. */
public interface MyTaskCounts {

	long getActive();

	long getOverdue();

	long getDueToday();

	long getUpcoming();

	long getInProgress();

	long getBlocked();

	long getCompletedThisWeek();

}
