package com.teamops.backup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.backup.service.BackupRetention.Candidate;

/** Retention: the newest backup of each of the last 30 days and of each of the last 12 months is kept. */
class BackupRetentionTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

	@Test
	void keepsThirtyDailyAndTwelveMonthly() {
		// One nightly backup a day for 500 days, ending 16 Oct 2026.
		List<Candidate> nightly = new ArrayList<>();
		LocalDate last = LocalDate.of(2026, 10, 16);
		for (int i = 0; i < 500; i++) {
			nightly.add(at((long) i, last.minusDays(i), LocalTime.of(2, 0)));
		}
		Set<Long> delete = BackupRetention.toDelete(nightly, 30, 12, ZONE);
		List<LocalDate> kept = nightly.stream()
			.filter(c -> !delete.contains(c.id()))
			.map(c -> c.startedAt().atZone(ZONE).toLocalDate())
			.toList();

		// 30 days (17 Sep - 16 Oct) plus the last backup of each earlier month back to Nov 2025: 12 months in all,
		// the two newest months (Oct, Sep 2026) being covered by daily ones.
		assertThat(kept).hasSize(30 + 10);
		assertThat(kept).contains(last, last.minusDays(29), LocalDate.of(2026, 8, 31), LocalDate.of(2025, 11, 30));
		assertThat(kept).doesNotContain(last.minusDays(30), LocalDate.of(2025, 10, 31), LocalDate.of(2026, 8, 30));
	}

	@Test
	void sameDayExtrasGoButTheNewestOfTheDayStays() {
		LocalDate day = LocalDate.of(2026, 10, 16);
		List<Candidate> backups = List.of(at(1L, day, LocalTime.of(2, 0)), at(2L, day, LocalTime.of(14, 30)),
				at(3L, day.minusDays(1), LocalTime.of(2, 0)));
		assertThat(BackupRetention.toDelete(backups, 30, 12, ZONE)).containsExactly(1L);
	}

	@Test
	void daysAreBusinessTimeZoneDays() {
		// 20:00 UTC on 15 Oct is 01:30 on 16 Oct in India: same business day as the 02:00 run on the 16th.
		Candidate lateUtc = new Candidate(1L, Instant.parse("2026-10-15T20:00:00Z"));
		Candidate morning = at(2L, LocalDate.of(2026, 10, 16), LocalTime.of(2, 0));
		assertThat(BackupRetention.toDelete(List.of(lateUtc, morning), 30, 12, ZONE)).containsExactly(1L);
	}

	@Test
	void aGapOfFailedNightsNeverDeletesTheLastGoodBackups() {
		// Nothing for 60 days, then nothing new: the 3 old backups are the newest there are, so they stay.
		List<Candidate> old = List.of(at(1L, LocalDate.of(2026, 6, 1), LocalTime.of(2, 0)),
				at(2L, LocalDate.of(2026, 6, 2), LocalTime.of(2, 0)), at(3L, LocalDate.of(2026, 6, 3), LocalTime.of(2, 0)));
		assertThat(BackupRetention.toDelete(old, 30, 12, ZONE)).isEmpty();
		assertThat(BackupRetention.toDelete(List.of(), 30, 12, ZONE)).isEmpty();
	}

	private static Candidate at(Long id, LocalDate day, LocalTime time) {
		return new Candidate(id, day.atTime(time).atZone(ZONE).toInstant());
	}

}
