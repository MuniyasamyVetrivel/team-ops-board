package com.teamops.task.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.project.dto.ProjectRef;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.user.dto.UserSummary;

/** Task row for lists. Department, project and assignee are fetched with the page (no N+1). */
public record TaskListItem(Long id, String code, String title, TaskStatus status, TaskPriority priority,
		DepartmentSummary department, ProjectRef project, UserSummary assignee, LocalDate dueDate, DueState dueState,
		BigDecimal estimatedHours, Instant completedAt, Instant updatedAt) {

	public static TaskListItem of(Task task, LocalDate today) {
		return new TaskListItem(task.getId(), task.getCode(), task.getTitle(), task.getStatus(), task.getPriority(),
				DepartmentSummary.of(task.getDepartment()), ProjectRef.of(task.getProject()),
				UserSummary.of(task.getAssignee()), task.getDueDate(),
				DueState.of(task.getDueDate(), task.getStatus(), today), task.getEstimatedHours(),
				task.getCompletedAt(), task.getUpdatedAt());
	}

}
