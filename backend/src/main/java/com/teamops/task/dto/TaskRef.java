package com.teamops.task.dto;

import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskStatus;

/** Compact task reference (dependencies). */
public record TaskRef(Long id, String code, String title, TaskStatus status) {

	public static TaskRef of(Task task) {
		return new TaskRef(task.getId(), task.getCode(), task.getTitle(), task.getStatus());
	}

}
