package com.teamops.task.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.project.dto.ProjectRef;
import com.teamops.task.dto.TaskChildDtos.AttachmentResponse;
import com.teamops.task.dto.TaskChildDtos.ChecklistItemResponse;
import com.teamops.task.dto.TaskChildDtos.CommentResponse;
import com.teamops.task.dto.TaskChildDtos.HistoryEntry;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskSource;
import com.teamops.task.entity.TaskStatus;
import com.teamops.user.dto.UserSummary;

/**
 * Everything the task drawer shows, in one response. {@code version} must be sent back on edits (optimistic
 * locking).
 */
public record TaskDetail(
		Long id,
		String code,
		String title,
		String description,
		TaskStatus status,
		TaskPriority priority,
		DepartmentSummary department,
		ProjectRef project,
		UserSummary assignee,
		UserSummary createdBy,
		LocalDate startDate,
		LocalDate dueDate,
		DueState dueState,
		BigDecimal estimatedHours,
		BigDecimal actualHours,
		Instant completedAt,
		TaskSource source,
		Instant createdAt,
		Instant updatedAt,
		Integer version,
		List<String> tags,
		List<UserSummary> watchers,
		List<TaskRef> dependencies,
		List<ChecklistItemResponse> checklist,
		List<CommentResponse> comments,
		List<AttachmentResponse> attachments,
		List<HistoryEntry> history,
		TaskPermissions permissions) {

}
