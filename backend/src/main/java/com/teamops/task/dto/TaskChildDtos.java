package com.teamops.task.dto;

import java.time.Instant;

import com.teamops.task.entity.TaskAttachment;
import com.teamops.task.entity.TaskChecklistItem;
import com.teamops.task.entity.TaskComment;
import com.teamops.task.entity.TaskHistory;
import com.teamops.user.dto.UserSummary;

/** Response records for a task's child collections. */
public final class TaskChildDtos {

	private TaskChildDtos() {
	}

	public record CommentResponse(Long id, UserSummary author, String body, boolean edited, Instant createdAt,
			Instant updatedAt) {

		public static CommentResponse of(TaskComment comment) {
			return new CommentResponse(comment.getId(), UserSummary.of(comment.getAuthor()), comment.getBody(),
					comment.isEdited(), comment.getCreatedAt(), comment.getUpdatedAt());
		}

	}

	public record ChecklistItemResponse(Long id, String content, boolean done, UserSummary doneBy, Instant doneAt,
			int position) {

		public static ChecklistItemResponse of(TaskChecklistItem item) {
			return new ChecklistItemResponse(item.getId(), item.getContent(), item.isDone(),
					UserSummary.of(item.getDoneBy()), item.getDoneAt(), item.getPosition());
		}

	}

	public record HistoryEntry(Long id, UserSummary changedBy, String field, String oldValue, String newValue,
			Instant changedAt) {

		public static HistoryEntry of(TaskHistory history) {
			return new HistoryEntry(history.getId(), UserSummary.of(history.getChangedBy()), history.getFieldName(),
					history.getOldValue(), history.getNewValue(), history.getChangedAt());
		}

	}

	public record AttachmentResponse(Long fileId, String fileName, String contentType, long sizeBytes,
			UserSummary addedBy, Instant addedAt) {

		public static AttachmentResponse of(TaskAttachment attachment) {
			return new AttachmentResponse(attachment.getFile().getId(), attachment.getFile().getOriginalName(),
					attachment.getFile().getContentType(), attachment.getFile().getSizeBytes(),
					UserSummary.of(attachment.getAddedBy()), attachment.getAddedAt());
		}

	}

}
