package com.teamops.backup.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which successful backups to delete: the newest backup of each of the {@code keepDaily} most recent days and of each
 * of the {@code keepMonthly} most recent months is kept (days and months in the business time zone), everything else
 * goes. Counting days that have a backup, rather than calendar days, means a run of failed nights never deletes the
 * last good backups.
 */
public final class BackupRetention {

	public record Candidate(Long id, Instant startedAt) {

	}

	private BackupRetention() {
	}

	public static Set<Long> toDelete(List<Candidate> successful, int keepDaily, int keepMonthly, ZoneId zone) {
		List<Candidate> newestFirst = successful.stream()
			.sorted(Comparator.comparing(Candidate::startedAt).reversed())
			.toList();
		Set<Long> keep = new HashSet<>();
		Set<LocalDate> days = new HashSet<>();
		Set<YearMonth> months = new HashSet<>();
		for (Candidate backup : newestFirst) {
			LocalDate day = backup.startedAt().atZone(zone).toLocalDate();
			if (days.size() < keepDaily && days.add(day)) {
				keep.add(backup.id());
			}
			if (months.size() < keepMonthly && months.add(YearMonth.from(day))) {
				keep.add(backup.id());
			}
		}
		Set<Long> delete = new LinkedHashSet<>();
		newestFirst.stream().map(Candidate::id).filter(id -> !keep.contains(id)).forEach(delete::add);
		return delete;
	}

}
