package com.teamops.backup.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.teamops.backup.dto.BackupDtos.Problem;
import com.teamops.backup.entity.BackupRun;

/**
 * The backup alert rule: a problem when no backup has ever completed, when the last successful backup is older than
 * BACKUP_MAX_AGE_HOURS (default 26), when the last finished run failed, or when backups cannot run at all.
 */
public final class BackupHealth {

	public static final String NO_BACKUP = "NO_BACKUP";

	public static final String STALE = "STALE";

	public static final String LAST_FAILED = "LAST_FAILED";

	public static final String NOT_CONFIGURED = "NOT_CONFIGURED";

	private BackupHealth() {
	}

	/**
	 * @param lastSuccess newest successful run, or null
	 * @param lastFinished newest successful or failed run, or null
	 * @param configurationError why backups cannot run (for example a missing key), or null
	 */
	public static List<Problem> problems(BackupRun lastSuccess, BackupRun lastFinished, String configurationError,
			Instant now, int maxAgeHours) {
		List<Problem> problems = new ArrayList<>();
		if (configurationError != null) {
			problems.add(new Problem(NOT_CONFIGURED, configurationError));
		}
		if (lastSuccess == null) {
			problems.add(new Problem(NO_BACKUP, "No backup has completed yet"));
		}
		else {
			Instant finished = lastSuccess.getFinishedAt() == null ? lastSuccess.getStartedAt()
					: lastSuccess.getFinishedAt();
			if (Duration.between(finished, now).compareTo(Duration.ofHours(maxAgeHours)) > 0) {
				problems.add(new Problem(STALE,
						"The last successful backup is older than %d hours".formatted(maxAgeHours)));
			}
		}
		if (lastFinished != null && lastFinished.getStatus() == BackupRun.Status.FAILED) {
			String reason = lastFinished.getErrorMessage() == null ? "" : ": " + lastFinished.getErrorMessage();
			problems.add(new Problem(LAST_FAILED, "The last backup failed" + reason));
		}
		return problems;
	}

}
