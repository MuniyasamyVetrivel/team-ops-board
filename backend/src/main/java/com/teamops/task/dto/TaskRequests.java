package com.teamops.task.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for the task API. */
public final class TaskRequests {

	private TaskRequests() {
	}

	/**
	 * @param departmentId defaults to the creator's primary department
	 * @param assigneeId optional; assigning someone else needs TASK_ASSIGN
	 */
	public record CreateTask(
			@NotBlank(message = "Title is required") @Size(max = 250) String title,
			@Size(max = 20000) String description,
			Long departmentId,
			Long projectId,
			Long assigneeId,
			TaskPriority priority,
			LocalDate startDate,
			LocalDate dueDate,
			@DecimalMin(value = "0", message = "Hours cannot be negative") @DecimalMax(value = "9999", message = "Too many hours") BigDecimal estimatedHours,
			@Size(max = 10, message = "At most 10 tags") List<@Size(max = 40) String> tags) {

	}

	/** Full replacement of the editable fields. {@code version} must match the current task. */
	public record UpdateTask(
			@NotNull(message = "Version is required") Integer version,
			@NotBlank(message = "Title is required") @Size(max = 250) String title,
			@Size(max = 20000) String description,
			@NotNull(message = "Department is required") Long departmentId,
			Long projectId,
			@NotNull(message = "Priority is required") TaskPriority priority,
			LocalDate startDate,
			LocalDate dueDate,
			@DecimalMin(value = "0", message = "Hours cannot be negative") @DecimalMax(value = "9999", message = "Too many hours") BigDecimal estimatedHours,
			@DecimalMin(value = "0", message = "Hours cannot be negative") @DecimalMax(value = "9999", message = "Too many hours") BigDecimal actualHours,
			@Size(max = 10, message = "At most 10 tags") List<@Size(max = 40) String> tags) {

	}

	/** {@code assigneeId = null} unassigns. */
	public record Assign(Long assigneeId) {

	}

	public record ChangeStatus(@NotNull(message = "Status is required") TaskStatus status) {

	}

	public record Comment(@NotBlank(message = "Comment cannot be empty") @Size(max = 5000) String body) {

	}

	public record ChecklistItem(@NotBlank(message = "Item cannot be empty") @Size(max = 500) String content) {

	}

	public record ChecklistToggle(boolean done) {

	}

	public record Dependency(@NotNull(message = "Task is required") Long dependsOnTaskId) {

	}

	public record Watcher(@NotNull(message = "User is required") Long userId) {

	}

}
