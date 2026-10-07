package com.teamops.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.teamops.dashboard.DashboardDtos.WeekPoint;

/** Pure calculations behind the dashboard. Division by zero gives {@code null} (shown as "—"). */
public final class DashboardMath {

	private DashboardMath() {
	}

	/** Whole percent, half-up; {@code null} when {@code whole} is zero. */
	public static Integer percent(long part, long whole) {
		if (whole == 0) {
			return null;
		}
		return BigDecimal.valueOf(part)
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP)
			.intValue();
	}

	/** Whole percent, half-up; {@code null} when {@code whole} is zero or missing. */
	public static Integer percent(BigDecimal part, BigDecimal whole) {
		if (whole == null || whole.signum() == 0) {
			return null;
		}
		BigDecimal numerator = part == null ? BigDecimal.ZERO : part;
		return numerator.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.HALF_UP).intValue();
	}

	/** Counts for one week as returned by the aggregate query. */
	public record WeekCounts(long completed, long withDueDate, long onTime) {

		static final WeekCounts EMPTY = new WeekCounts(0, 0, 0);

	}

	/**
	 * The {@code weeks} most recent weeks, oldest first, ending with the week starting {@code currentWeekStart}.
	 * Weeks with no completions are filled with zeros so the chart has no gaps.
	 */
	public static List<WeekPoint> weeklySeries(LocalDate currentWeekStart, int weeks, Map<LocalDate, WeekCounts> counts) {
		List<WeekPoint> series = new ArrayList<>(weeks);
		for (int i = weeks - 1; i >= 0; i--) {
			LocalDate start = currentWeekStart.minusWeeks(i);
			WeekCounts week = counts.getOrDefault(start, WeekCounts.EMPTY);
			series.add(new WeekPoint(start, week.completed(), week.onTime(), percent(week.onTime(), week.withDueDate())));
		}
		return series;
	}

}
