package com.teamops.backup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.teamops.backup.dto.BackupDtos.Problem;
import com.teamops.backup.entity.BackupRun;

/** The alert rule: last success older than 26 hours, last run failed, never backed up, or not configured. */
class BackupHealthTest {

	private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

	@Test
	void aRecentSuccessIsHealthy() {
		BackupRun ok = run(BackupRun.Status.SUCCESS, NOW.minus(Duration.ofHours(10)));
		assertThat(BackupHealth.problems(ok, ok, null, NOW, 26)).isEmpty();
	}

	@Test
	void olderThan26HoursIsStale() {
		BackupRun exactly = run(BackupRun.Status.SUCCESS, NOW.minus(Duration.ofHours(26)));
		assertThat(BackupHealth.problems(exactly, exactly, null, NOW, 26)).isEmpty();
		BackupRun old = run(BackupRun.Status.SUCCESS, NOW.minus(Duration.ofHours(26)).minusSeconds(1));
		assertThat(BackupHealth.problems(old, old, null, NOW, 26)).extracting(Problem::code)
			.containsExactly(BackupHealth.STALE);
	}

	@Test
	void aFailedLastRunIsAProblemEvenAfterARecentSuccess() {
		BackupRun ok = run(BackupRun.Status.SUCCESS, NOW.minus(Duration.ofHours(5)));
		BackupRun failed = run(BackupRun.Status.FAILED, NOW.minus(Duration.ofHours(1)));
		failed.setErrorMessage("mysqldump failed (exit 2)");
		assertThat(BackupHealth.problems(ok, failed, null, NOW, 26)).singleElement()
			.satisfies(p -> {
				assertThat(p.code()).isEqualTo(BackupHealth.LAST_FAILED);
				assertThat(p.message()).contains("mysqldump failed");
			});
	}

	@Test
	void neverBackedUpAndMissingKeyAreProblems() {
		assertThat(BackupHealth.problems(null, null, "BACKUP_ENCRYPTION_KEY is not set", NOW, 26))
			.extracting(Problem::code)
			.containsExactly(BackupHealth.NOT_CONFIGURED, BackupHealth.NO_BACKUP);
	}

	private static BackupRun run(BackupRun.Status status, Instant finished) {
		BackupRun run = new BackupRun(BackupRun.Trigger.SCHEDULED, finished.minusSeconds(60), "LOCAL x", null);
		run.setStatus(status);
		run.setFinishedAt(finished);
		return run;
	}

}
