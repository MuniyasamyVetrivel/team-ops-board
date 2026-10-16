package com.teamops.backup.dto;

import java.time.Instant;
import java.util.List;

import com.teamops.backup.entity.BackupRun;

public final class BackupDtos {

	private BackupDtos() {
	}

	/**
	 * One backup attempt in the history. {@code status}, {@code target}, {@code errorMessage} and {@code removedAt} are
	 * the first copy; {@code secondary} is the second copy, or null when no second target was configured.
	 */
	public record Run(Long id, String trigger, String status, Instant startedAt, Instant finishedAt, String fileName,
			Long sizeBytes, String checksum, String target, Integer uploadFiles, String errorMessage,
			Long requestedById, Instant removedAt, Copy secondary) {

		public static Run of(BackupRun run) {
			return run == null ? null
					: new Run(run.getId(), run.getTrigger().name(), run.getStatus().name(), run.getStartedAt(),
							run.getFinishedAt(), run.getFileName(), run.getSizeBytes(), run.getChecksum(),
							run.getTarget(), run.getUploadFiles(), run.getErrorMessage(), run.getRequestedById(),
							run.getRemovedAt(), Copy.secondaryOf(run));
		}

	}

	/** One stored copy of a backup file. */
	public record Copy(String target, String status, String errorMessage, Instant removedAt) {

		static Copy secondaryOf(BackupRun run) {
			return run.getSecondaryStatus() == null ? null
					: new Copy(run.getSecondaryTarget(), run.getSecondaryStatus().name(), run.getSecondaryError(),
							run.getSecondaryRemovedAt());
		}

	}

	public record Problem(String code, String message) {

	}

	/**
	 * The health of one copy: PRIMARY (BACKUP_TARGET) or SECONDARY (BACKUP_SECONDARY_TARGET), with its own problems
	 * ({@code NO_BACKUP}, {@code STALE}, {@code LAST_FAILED}; prefixed {@code SECONDARY_} for the second copy).
	 */
	public record CopyStatus(String copy, String target, boolean healthy, List<Problem> problems,
			Instant lastSuccessAt, String lastStatus, String lastError) {

	}

	/**
	 * The backup status page: overall health (every copy) with all problems, each copy's own health, the last
	 * successful and last finished run (first copy), the next scheduled run (null when the schedule is off), and the
	 * retention policy.
	 */
	public record Status(boolean healthy, List<Problem> problems, List<CopyStatus> copies, Run lastSuccess,
			Run lastRun, boolean running, boolean scheduleEnabled, String schedule, String timeZone, Instant nextRunAt,
			String target, String secondaryTarget, int keepDaily, int keepMonthly, int maxAgeHours) {

	}

}
