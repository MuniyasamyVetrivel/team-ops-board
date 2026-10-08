package com.teamops.marketing.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Marketing rate formulas (brief section 80). Every result is computed, never stored. A zero or missing denominator
 * gives {@code null}, which the UI shows as "—". Percentages and money are rounded half-up to two decimals.
 */
public final class MarketingMath {

	private static final int SCALE = 2;

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private MarketingMath() {
	}

	/** part ÷ whole × 100, e.g. 8,500 ÷ 24,000 = 35.42. */
	public static BigDecimal percent(Number part, Number whole) {
		BigDecimal denominator = decimal(whole);
		if (part == null || denominator == null || denominator.signum() == 0) {
			return null;
		}
		return decimal(part).multiply(HUNDRED).divide(denominator, SCALE, RoundingMode.HALF_UP);
	}

	/** amount ÷ count, e.g. cost per lead ₹42,000 ÷ 84 = ₹500.00. */
	public static BigDecimal perUnit(Number amount, Number count) {
		BigDecimal denominator = decimal(count);
		if (amount == null || denominator == null || denominator.signum() == 0) {
			return null;
		}
		return decimal(amount).divide(denominator, SCALE, RoundingMode.HALF_UP);
	}

	/** max(target − actual, 0); a missing actual counts as zero. */
	public static BigDecimal remaining(Number target, Number actual) {
		if (target == null) {
			return null;
		}
		BigDecimal left = decimal(target).subtract(actual == null ? BigDecimal.ZERO : decimal(actual));
		return left.signum() < 0 ? BigDecimal.ZERO : left;
	}

	/** Email open rate = unique opens ÷ delivered × 100. */
	public static BigDecimal openRate(Number uniqueOpens, Number delivered) {
		return percent(uniqueOpens, delivered);
	}

	/** Email click rate = unique clicks ÷ delivered × 100. */
	public static BigDecimal clickRate(Number uniqueClicks, Number delivered) {
		return percent(uniqueClicks, delivered);
	}

	/** Email lead conversion = leads ÷ delivered × 100. */
	public static BigDecimal leadConversion(Number leads, Number delivered) {
		return percent(leads, delivered);
	}

	/** Paid CTR = clicks ÷ impressions × 100. */
	public static BigDecimal clickThroughRate(Number clicks, Number impressions) {
		return percent(clicks, impressions);
	}

	/** Paid cost per lead = spend ÷ leads. */
	public static BigDecimal costPerLead(Number spend, Number leads) {
		return perUnit(spend, leads);
	}

	/** Paid conversion rate = conversions ÷ leads × 100. */
	public static BigDecimal conversionRate(Number conversions, Number leads) {
		return percent(conversions, leads);
	}

	/** Remaining budget = budget − spent. Negative means the campaign overspent, so it is not clamped. */
	public static BigDecimal remainingBudget(Number budget, Number spent) {
		if (budget == null) {
			return null;
		}
		return decimal(budget).subtract(spent == null ? BigDecimal.ZERO : decimal(spent));
	}

	private static BigDecimal decimal(Number value) {
		if (value == null) {
			return null;
		}
		if (value instanceof BigDecimal big) {
			return big;
		}
		if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
			return BigDecimal.valueOf(value.longValue());
		}
		return new BigDecimal(value.toString());
	}

}
