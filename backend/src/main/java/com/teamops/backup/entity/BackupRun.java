package com.teamops.backup.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.audit.AuditIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One backup attempt. A log, not an editable record: its business audit entries (BACKUP_COMPLETED, BACKUP_FAILED,
 * BACKUP_REMOVED) tell the story, so the automatic record history leaves it out.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@AuditIgnore
@Table(name = "backup_runs")
public class BackupRun {

	public enum Trigger {

		SCHEDULED, MANUAL

	}

	public enum Status {

		RUNNING, SUCCESS, FAILED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "trigger_type", nullable = false, length = 20)
	private Trigger trigger;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 20)
	private Status status;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "file_name", length = 255)
	private String fileName;

	@Column(name = "size_bytes")
	private Long sizeBytes;

	@Column(name = "checksum", length = 64)
	private String checksum;

	@Column(name = "target", nullable = false, length = 500)
	private String target;

	@Column(name = "upload_files")
	private Integer uploadFiles;

	@Column(name = "error_message", length = 2000)
	private String errorMessage;

	@Column(name = "requested_by")
	private Long requestedById;

	@Column(name = "removed_at")
	private Instant removedAt;

	public BackupRun(Trigger trigger, Instant startedAt, String target, Long requestedById) {
		this.trigger = trigger;
		this.status = Status.RUNNING;
		this.startedAt = startedAt;
		this.target = target;
		this.requestedById = requestedById;
	}

}
