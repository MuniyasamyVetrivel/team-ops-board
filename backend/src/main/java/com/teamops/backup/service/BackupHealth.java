package com.teamops.backup.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.teamops.backup.dto.BackupDtos.Problem;

/**
 * The backup alert rule, applied to each copy on its own: a problem when no copy has ever been stored, when the last
 * successful one is older than BACKUP_MAX_AGE_HOURS (default 26), or when the last finished run failed to store it.
 * The second copy's codes carry a {@code SECONDARY_} prefix. Backups that cannot run at all are reported once.
 */
public final class BackupHealth {

	public static final String NO_BACKUP = "NO_BACKUP";

	public static final String STALE = "STALE";

	public static final String LAST_FAILED = "LAST_FAILED";

	public static final String NOT_CONFIGURED = "NOT_CONFIGURED";

	public static final String SECONDARY = "SECONDARY_";

	/**
	 * What is known about one copy.
	 *
	 * @param lastSuccessAt when the newest successful copy was stored, or null
	 * @param lastFailed whether the newest finished run failed to store this copy
	 * @param lastError why it failed
	 */
	public record CopyState(Instant lastSuccessAt, boolean lastFailed, String lastError) {

	}

	private BackupHealth() {
	}

	/** Problems of the first copy (plain codes) or the second ({@code SECONDARY_} codes). */
	public static List<Problem> problems(BackupTargets.Copy copy, CopyState state, Instant now, int maxAgeHours) {
		boolean second = copy == BackupTargets.Copy.SECONDARY;
		String prefix = second ? SECONDARY : "";
		List<Problem> problems = new ArrayList<>();
		if (state.lastSuccessAt() == null) {
			problems.add(new Problem(prefix + NO_BACKUP,
					second ? "No second copy of a backup has been stored yet" : "No backup has completed yet"));
		}
		else if (Duration.between(state.lastSuccessAt(), now).compareTo(Duration.ofHours(maxAgeHours)) > 0) {
			problems.add(new Problem(prefix + STALE, (second ? "The last second copy" : "The last successful backup")
					+ " is older than %d hours".formatted(maxAgeHours)));
		}
		if (state.lastFailed()) {
			String reason = state.lastError() == null ? "" : ": " + state.lastError();
			problems.add(new Problem(prefix + LAST_FAILED,
					(second ? "The second copy of the last backup failed" : "The last backup failed") + reason));
		}
		return problems;
	}

	public static Problem notConfigured(String reason) {
		return new Problem(NOT_CONFIGURED, reason);
	}

}
