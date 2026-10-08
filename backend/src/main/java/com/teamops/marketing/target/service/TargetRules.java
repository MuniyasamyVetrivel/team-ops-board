package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.target.entity.TargetUnit;

/**
 * Monthly target rules (brief sections 30–33). History is never overwritten: a month's target can be changed only
 * while the month is open (any future month, the current business month and the one before it), except by a Super
 * Admin. Actuals are never entered for future months.
 */
public final class TargetRules {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private TargetRules() {
	}

	/** Future months, the current business month and the month before it. */
	public static boolean isOpen(MarketingPeriod period, LocalDate today) {
		return !period.firstDay().isBefore(MarketingPeriod.of(today).previous().firstDay());
	}

	public static boolean canChange(MarketingPeriod period, LocalDate today, boolean superAdmin) {
		return superAdmin || isOpen(period, today);
	}

	public static boolean isFuture(MarketingPeriod period, LocalDate today) {
		return period.firstDay().isAfter(MarketingPeriod.of(today).firstDay());
	}

	/**
	 * Checks a value against the type's unit: counts are whole numbers, percentages at most 100, money at most two
	 * decimals (enforced by the column).
	 *
	 * @param field the request field, for the error message
	 */
	public static BigDecimal requireValid(TargetUnit unit, BigDecimal value, String field) {
		if (value == null) {
			return null;
		}
		if (unit == TargetUnit.COUNT && value.stripTrailingZeros().scale() > 0) {
			throw ApiException.badRequest("INVALID_VALUE", "The " + field + " must be a whole number for this target type");
		}
		if (unit == TargetUnit.PERCENT && value.compareTo(HUNDRED) > 0) {
			throw ApiException.badRequest("INVALID_VALUE", "The " + field + " is a percentage and cannot exceed 100");
		}
		return value;
	}

	/** Where a target's actual came from. */
	public enum ActualOrigin {

		/** Entered by hand (manual types, or a source whose module did not exist yet; such values are kept). */
		MANUAL,
		/** Aggregated from the underlying records. */
		AUTOMATIC,
		/** Nothing recorded yet. */
		NONE

	}

	public record ResolvedActual(BigDecimal value, ActualOrigin origin) {
	}

	/**
	 * A stored actual always wins, so a month entered by hand before its automatic source existed never changes
	 * afterwards. Otherwise an automatic type uses the computed value.
	 */
	public static ResolvedActual resolve(BigDecimal stored, boolean automatic, BigDecimal computed) {
		if (stored != null) {
			return new ResolvedActual(stored, ActualOrigin.MANUAL);
		}
		if (automatic && computed != null) {
			return new ResolvedActual(computed, ActualOrigin.AUTOMATIC);
		}
		return new ResolvedActual(null, ActualOrigin.NONE);
	}

	/**
	 * Combines monthly values into a quarter or a year: counts and money add up, percentages are averaged. Months
	 * without a value are skipped; {@code null} when none has one.
	 */
	public static BigDecimal combine(TargetUnit unit, List<BigDecimal> values) {
		List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
		if (present.isEmpty()) {
			return null;
		}
		BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		return unit == TargetUnit.PERCENT ? sum.divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP)
				: sum;
	}

}
