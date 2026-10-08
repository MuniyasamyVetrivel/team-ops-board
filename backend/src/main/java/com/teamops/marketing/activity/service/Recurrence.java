package com.teamops.marketing.activity.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

import com.teamops.marketing.activity.entity.Frequency;

/**
 * Occurrence periods for each frequency (brief section 35). Periods follow the calendar: a day, an ISO week (Monday
 * to Sunday), a month, a quarter or a year. An occurrence is due {@code dueOffsetDays} after its period starts, but
 * never after the period ends.
 */
public final class Recurrence {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private Recurrence() {
	}

	/** One occurrence period, both days inclusive. */
	public record Period(LocalDate start, LocalDate end) {

		public boolean contains(LocalDate date) {
			return !date.isBefore(start) && !date.isAfter(end);
		}

	}

	/** The period of {@code frequency} that contains {@code date}. */
	public static Period containing(Frequency frequency, LocalDate date) {
		LocalDate start = switch (frequency) {
			case DAILY -> date;
			case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case MONTHLY -> date.withDayOfMonth(1);
			case QUARTERLY -> date.withMonth(((date.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
			case YEARLY -> date.withDayOfYear(1);
		};
		return new Period(start, endOf(frequency, start));
	}

	/** The period right after {@code period}. */
	public static Period next(Frequency frequency, Period period) {
		LocalDate start = period.end().plusDays(1);
		return new Period(start, endOf(frequency, start));
	}

	/** Due {@code offsetDays} after the period starts, clamped to the period's last day. */
	public static LocalDate dueDate(Period period, int offsetDays) {
		LocalDate due = period.start().plusDays(Math.max(offsetDays, 0));
		return due.isAfter(period.end()) ? period.end() : due;
	}

	/**
	 * Whether an activity running from {@code startDate} to {@code endDate} (open-ended when {@code null}) covers the
	 * period: the period overlaps the activity's dates.
	 */
	public static boolean covers(Period period, LocalDate startDate, LocalDate endDate) {
		return !period.end().isBefore(startDate) && (endDate == null || !period.start().isAfter(endDate));
	}

	/** "8 Oct 2026", "Week of 5 Oct 2026", "October 2026", "Q4 2026", "2026". */
	public static String label(Frequency frequency, Period period) {
		LocalDate start = period.start();
		return switch (frequency) {
			case DAILY -> DAY.format(start);
			case WEEKLY -> "Week of " + DAY.format(start);
			case MONTHLY -> monthName(start.getMonth()) + " " + start.getYear();
			case QUARTERLY -> "Q" + start.get(IsoFields.QUARTER_OF_YEAR) + " " + start.getYear();
			case YEARLY -> String.valueOf(start.getYear());
		};
	}

	/**
	 * Fills a task title template. Placeholders: {month} ("October"), {year} ("2026"), {quarter} ("Q4"), {week}
	 * ("Week 41"), {date} ("1 Oct 2026") and {period} (the period label). Unknown text is kept as written.
	 */
	public static String title(String template, Frequency frequency, Period period) {
		LocalDate start = period.start();
		return template.replace("{month}", monthName(start.getMonth()))
			.replace("{year}", String.valueOf(start.getYear()))
			.replace("{quarter}", "Q" + start.get(IsoFields.QUARTER_OF_YEAR))
			.replace("{week}", "Week " + start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
			.replace("{date}", DAY.format(start))
			.replace("{period}", label(frequency, period))
			.trim();
	}

	private static LocalDate endOf(Frequency frequency, LocalDate start) {
		return switch (frequency) {
			case DAILY -> start;
			case WEEKLY -> start.plusDays(6);
			case MONTHLY -> start.with(TemporalAdjusters.lastDayOfMonth());
			case QUARTERLY -> start.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
			case YEARLY -> start.with(TemporalAdjusters.lastDayOfYear());
		};
	}

	private static String monthName(Month month) {
		return month.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
	}

}
