package com.teamops.backup.dto;

import java.time.Instant;
import java.util.List;

import com.teamops.backup.entity.BackupRun;

public final class BackupDtos {

	private BackupDtos() {
	}

	/** One backup attempt in the history. */
	public record Run(Long id, String trigger, String status, Instant startedAt, Instant finishedAt, String fileName,
			Long sizeBytes, String checksum, String target, Integer uploadFiles, String errorMessage,
			Long requestedById, Instant removedAt) {

		public static Run of(BackupRun run) {
			return run == null ? null
					: new Run(run.getId(), run.getTrigger().name(), run.getStatus().name(), run.getStartedAt(),
							run.getFinishedAt(), run.getFileName(), run.getSizeBytes(), run.getChecksum(),
							run.getTarget(), run.getUploadFiles(), run.getErrorMessage(), run.getRequestedById(),
							run.getRemovedAt());
		}

	}

	public record Problem(String code, String message) {

	}

	/**
	 * The backup status page: health, last successful and last run, the next scheduled run (null when the schedule
	 * is off), where backups go, and the retention policy.
	 */
	public record Status(boolean healthy, List<Problem> problems, Run lastSuccess, Run lastRun, boolean running,
			boolean scheduleEnabled, String schedule, String timeZone, Instant nextRunAt, String target, int keepDaily,
			int keepMonthly, int maxAgeHours) {

	}

}
