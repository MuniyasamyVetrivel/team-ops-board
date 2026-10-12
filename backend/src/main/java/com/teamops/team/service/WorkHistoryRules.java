package com.teamops.team.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.teamops.team.dto.WorkHistoryDtos.WeekCount;

/** The work history arithmetic: timeliness, days to complete and the weekly completion series. */
public final class WorkHistoryRules {

	/** Weeks shown when no date range is given. */
	static final int DEFAULT_WEEKS = 12;

	/** At most two years of weeks. */
	static final int MAX_WEEKS = 106;

	private WorkHistoryRules() {
	}

	/** Completed on or before the due date; null without a due date. */
	public static Boolean onTime(LocalDate completed, LocalDate due) {
		return due == null || completed == null ? null : !completed.isAfter(due);
	}

	/**
	 * Days after the due date: for completed work, how late it finished (null when on time); for open work, how long it
	 * has been overdue (null when not overdue).
	 */
	public static Integer daysLate(LocalDate due, LocalDate completed, LocalDate today) {
		if (due == null) {
			return null;
		}
		LocalDate end = completed != null ? completed : today;
		long days = ChronoUnit.DAYS.between(due, end);
		return days > 0 ? (int) days : null;
	}

	/** Days from start (or creation, when there is no start or it is after completion) to completion, never negative. */
	public static long daysToComplete(LocalDate start, LocalDate created, LocalDate completed) {
		LocalDate from = start != null && !start.isAfter(completed) ? start : created;
		return Math.max(ChronoUnit.DAYS.between(from, completed), 0);
	}

	/** {@code part} of {@code whole} as a percentage with one decimal; null when {@code whole} is 0. */
	public static BigDecimal percent(long part, long whole) {
		return whole == 0 ? null
				: BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
	}

	/** Average with one decimal; null when there is nothing to average. */
	public static BigDecimal average(Collection<Long> values) {
		if (values.isEmpty()) {
			return null;
		}
		long total = values.stream().mapToLong(Long::longValue).sum();
		return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
	}

	public static LocalDate weekOf(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/**
	 * The weeks (Mondays) the series covers: the weeks of [from, to]; from {@code from} to this week; the 12 weeks
	 * ending with {@code to} or with this week. At most {@value #MAX_WEEKS} weeks, keeping the latest.
	 */
	public static List<LocalDate> weeks(LocalDate from, LocalDate to, LocalDate today) {
		LocalDate last = weekOf(to != null ? to : from != null && from.isAfter(today) ? from : today);
		LocalDate first = from != null ? weekOf(from) : last.minusWeeks(DEFAULT_WEEKS - 1L);
		if (ChronoUnit.WEEKS.between(first, last) >= MAX_WEEKS) {
			first = last.minusWeeks(MAX_WEEKS - 1L);
		}
		List<LocalDate> weeks = new ArrayList<>();
		for (LocalDate week = first; !week.isAfter(last); week = week.plusWeeks(1)) {
			weeks.add(week);
		}
		return weeks;
	}

	/** Completions per week over {@code weeks}; completions outside them are not shown. */
	public static List<WeekCount> perWeek(List<LocalDate> weeks, Collection<LocalDate> completions) {
		Map<LocalDate, Long> counts = new TreeMap<>();
		weeks.forEach(week -> counts.put(week, 0L));
		for (LocalDate date : completions) {
			counts.computeIfPresent(weekOf(date), (week, count) -> count + 1);
		}
		return counts.entrySet().stream().map(e -> new WeekCount(e.getKey(), e.getValue())).toList();
	}

}
