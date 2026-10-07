package com.teamops.task.dto;

import java.util.Set;

import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;

/** Optional task filters; null or empty means "any". */
public record TaskSearchCriteria(String search, Set<TaskStatus> statuses, Set<TaskPriority> priorities,
		Long assigneeId, Long departmentId, Long projectId, DueFilter due, TaskView view) {

	public TaskSearchCriteria {
		statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
		priorities = priorities == null ? Set.of() : Set.copyOf(priorities);
		view = view == null ? TaskView.ALL : view;
	}

}
