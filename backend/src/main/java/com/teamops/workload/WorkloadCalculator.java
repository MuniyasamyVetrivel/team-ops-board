package com.teamops.workload;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Workload % = remaining hours of active work in the window ÷ (weekly capacity × window weeks) × 100.
 * <p>
 * Remaining hours per task = max(estimated − actual, 0), or the default task hours when there is no estimate. Active
 * tasks that are overdue, due within the window, or have no due date count; tasks due after the window do not.
 */
public final class WorkloadCalculator {

	private WorkloadCalculator() {
	}

	/** Hours of capacity in the window, e.g. 40 h/week × 14 days = 80 h. */
	public static BigDecimal capacityHours(BigDecimal weeklyCapacityHours, int windowDays) {
		return weeklyCapacityHours.multiply(BigDecimal.valueOf(windowDays))
			.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
	}

	/** Rounded to a whole percent; 0 when there is no capacity (never divides by zero). */
	public static int percent(BigDecimal remainingHours, BigDecimal weeklyCapacityHours, int windowDays) {
		BigDecimal capacity = capacityHours(weeklyCapacityHours, windowDays);
		if (capacity.signum() <= 0 || remainingHours == null) {
			return 0;
		}
		return remainingHours.multiply(BigDecimal.valueOf(100)).divide(capacity, 0, RoundingMode.HALF_UP).intValue();
	}

	/** Hours a single task still needs. */
	public static BigDecimal remainingHours(BigDecimal estimated, BigDecimal actual, BigDecimal defaultHours) {
		if (estimated == null) {
			return defaultHours;
		}
		BigDecimal remaining = estimated.subtract(actual == null ? BigDecimal.ZERO : actual);
		return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
	}

}
