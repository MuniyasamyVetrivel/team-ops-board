package com.teamops.task.dto;

import java.time.LocalDate;

import com.teamops.task.repository.MyTaskCounts;

/** Counts for the signed-in user's "My Tasks" cards. {@code today} is the business date they are based on. */
public record MyTaskSummary(LocalDate today, long active, long overdue, long dueToday, long upcoming,
		long inProgress, long blocked, long completedThisWeek) {

	public static MyTaskSummary of(MyTaskCounts counts, LocalDate today) {
		return new MyTaskSummary(today, counts.getActive(), counts.getOverdue(), counts.getDueToday(),
				counts.getUpcoming(), counts.getInProgress(), counts.getBlocked(), counts.getCompletedThisWeek());
	}

}
