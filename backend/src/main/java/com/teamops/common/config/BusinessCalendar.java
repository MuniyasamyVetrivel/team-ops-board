package com.teamops.common.config;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "Today" in the company's time zone (APP_TIME_ZONE, default Asia/Kolkata). Due-today and overdue must not depend
 * on the server's or the browser's time zone.
 */
@Component
public class BusinessCalendar {

	private final Clock clock;

	private final ZoneId zone;

	public BusinessCalendar(Clock clock, @Value("${app.time-zone:Asia/Kolkata}") String zone) {
		this.clock = clock;
		this.zone = ZoneId.of(zone);
	}

	public ZoneId zone() {
		return zone;
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(zone));
	}

	public Instant now() {
		return clock.instant();
	}

	/** Monday of the current week. */
	public LocalDate startOfWeek() {
		return today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/** Start of the given day in the business time zone, as an instant (for comparing with timestamps). */
	public Instant startOf(LocalDate date) {
		return date.atStartOfDay(zone).toInstant();
	}

}
