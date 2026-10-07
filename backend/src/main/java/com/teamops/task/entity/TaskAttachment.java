package com.teamops.task.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import com.teamops.common.storage.StoredFile;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "task_attachments")
public class TaskAttachment {

	@EmbeddedId
	private Id id;

	@MapsId("taskId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id")
	private Task task;

	@MapsId("fileId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id")
	private StoredFile file;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "added_by")
	private User addedBy;

	@CreationTimestamp
	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	public static TaskAttachment of(Task task, StoredFile file, User addedBy) {
		TaskAttachment attachment = new TaskAttachment();
		attachment.setId(new Id(task.getId(), file.getId()));
		attachment.setTask(task);
		attachment.setFile(file);
		attachment.setAddedBy(addedBy);
		return attachment;
	}

	@Getter
	@Embeddable
	@NoArgsConstructor
	public static class Id implements Serializable {

		@Column(name = "task_id")
		private Long taskId;

		@Column(name = "file_id")
		private Long fileId;

		public Id(Long taskId, Long fileId) {
			this.taskId = taskId;
			this.fileId = fileId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Id that && Objects.equals(taskId, that.taskId) && Objects.equals(fileId, that.fileId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(taskId, fileId);
		}

	}

}
