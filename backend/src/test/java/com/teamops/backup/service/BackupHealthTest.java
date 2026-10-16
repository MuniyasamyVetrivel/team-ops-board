package com.teamops.backup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.teamops.backup.dto.BackupDtos.Problem;
import com.teamops.backup.service.BackupHealth.CopyState;
import com.teamops.backup.service.BackupTargets.Copy;

/**
 * The alert rule, per copy: last success older than 26 hours, last run failed, or never stored. The second copy's
 * problems carry a SECONDARY_ prefix.
 */
class BackupHealthTest {

	private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

	@Test
	void aRecentSuccessIsHealthy() {
		assertThat(BackupHealth.problems(Copy.PRIMARY, success(Duration.ofHours(10)), NOW, 26)).isEmpty();
	}

	@Test
	void olderThan26HoursIsStale() {
		assertThat(BackupHealth.problems(Copy.PRIMARY, success(Duration.ofHours(26)), NOW, 26)).isEmpty();
		assertThat(BackupHealth.problems(Copy.PRIMARY, success(Duration.ofHours(26).plusSeconds(1)), NOW, 26))
			.extracting(Problem::code)
			.containsExactly(BackupHealth.STALE);
	}

	@Test
	void aFailedLastRunIsAProblemEvenAfterARecentSuccess() {
		CopyState state = new CopyState(NOW.minus(Duration.ofHours(5)), true, "mysqldump failed (exit 2)");
		assertThat(BackupHealth.problems(Copy.PRIMARY, state, NOW, 26)).singleElement().satisfies(p -> {
			assertThat(p.code()).isEqualTo(BackupHealth.LAST_FAILED);
			assertThat(p.message()).isEqualTo("The last backup failed: mysqldump failed (exit 2)");
		});
	}

	@Test
	void neverBackedUpIsAProblem() {
		assertThat(BackupHealth.problems(Copy.PRIMARY, new CopyState(null, false, null), NOW, 26))
			.extracting(Problem::code)
			.containsExactly(BackupHealth.NO_BACKUP);
	}

	@Test
	void theSecondCopyIsJudgedOnItsOwnWithItsOwnCodes() {
		CopyState stale = new CopyState(NOW.minus(Duration.ofHours(30)), true, "Upload to S3 failed: Access Denied");
		assertThat(BackupHealth.problems(Copy.SECONDARY, stale, NOW, 26)).extracting(Problem::code, Problem::message)
			.containsExactly(
					org.assertj.core.groups.Tuple.tuple("SECONDARY_STALE", "The last second copy is older than 26 hours"),
					org.assertj.core.groups.Tuple.tuple("SECONDARY_LAST_FAILED",
							"The second copy of the last backup failed: Upload to S3 failed: Access Denied"));
		assertThat(BackupHealth.problems(Copy.SECONDARY, new CopyState(null, false, null), NOW, 26))
			.extracting(Problem::code)
			.containsExactly("SECONDARY_NO_BACKUP");
	}

	private static CopyState success(Duration ago) {
		return new CopyState(NOW.minus(ago), false, null);
	}

}
