package com.teamops.bench.service;

import java.time.LocalDate;

/**
 * Where a client engagement stands on a given business day (from {@code BusinessCalendar}). Computed from the dates,
 * never stored: UPCOMING before the start date, ENDED after the end date, ACTIVE in between (both days included; an
 * engagement without an end date stays active).
 */
public enum EngagementStatus {

	UPCOMING, ACTIVE, ENDED;

	public static EngagementStatus of(LocalDate startDate, LocalDate endDate, LocalDate today) {
		if (today.isBefore(startDate)) {
			return UPCOMING;
		}
		if (endDate != null && today.isAfter(endDate)) {
			return ENDED;
		}
		return ACTIVE;
	}

}
