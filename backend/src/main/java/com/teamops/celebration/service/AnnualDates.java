package com.teamops.celebration.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Yearly occurrences of a date (birthdays, work anniversaries, yearly important days). A 29 February date falls on
 * 28 February in years that have no 29th.
 */
public final class AnnualDates {

	private AnnualDates() {
	}

	/** The date's month and day in {@code year}. */
	public static LocalDate in(LocalDate date, int year) {
		if (date.getMonth() == Month.FEBRUARY && date.getDayOfMonth() == 29 && !Year.isLeap(year)) {
			return LocalDate.of(year, Month.FEBRUARY, 28);
		}
		return date.withYear(year);
	}

	/**
	 * Occurrences in [from, to] (inclusive), earliest first.
	 * @param firstYear the first year that has an occurrence (e.g. the year after joining for anniversaries)
	 */
	public static List<LocalDate> between(LocalDate date, int firstYear, LocalDate from, LocalDate to) {
		List<LocalDate> dates = new ArrayList<>(2);
		for (int year = Math.max(from.getYear(), firstYear); year <= to.getYear(); year++) {
			LocalDate occurrence = in(date, year);
			if (!occurrence.isBefore(from) && !occurrence.isAfter(to)) {
				dates.add(occurrence);
			}
		}
		return dates;
	}

	/** The first occurrence on or after {@code day} (from {@code firstYear} on). */
	public static LocalDate next(LocalDate date, int firstYear, LocalDate day) {
		int year = Math.max(day.getYear(), firstYear);
		LocalDate occurrence = in(date, year);
		return occurrence.isBefore(day) ? in(date, year + 1) : occurrence;
	}

	/** Birthdays from the year after birth. */
	public static int firstBirthdayYear(LocalDate dateOfBirth) {
		return dateOfBirth.getYear() + 1;
	}

	/** Work anniversaries from the first full year after joining. */
	public static int firstAnniversaryYear(LocalDate dateOfJoining) {
		return dateOfJoining.getYear() + 1;
	}

	/** Completed years on an occurrence, e.g. 5 for the fifth work anniversary. */
	public static int years(LocalDate date, LocalDate occurrence) {
		return occurrence.getYear() - date.getYear();
	}

}
