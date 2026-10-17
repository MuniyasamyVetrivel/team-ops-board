package com.teamops.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Budget against actual spend, both in AUD, computed and never stored: remaining = max(budget − actual, 0), over by =
 * max(actual − budget, 0), used % = actual ÷ budget × 100 (null for a zero budget: division by zero gives null), and a
 * department is over budget when actual is above the budget.
 */
public final class BudgetMath {

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private BudgetMath() {
	}

	public static BigDecimal remaining(BigDecimal budget, BigDecimal actual) {
		return budget.subtract(actual).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
	}

	public static BigDecimal overBy(BigDecimal budget, BigDecimal actual) {
		return actual.subtract(budget).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
	}

	/** One decimal; null for a zero budget. */
	public static BigDecimal usedPercent(BigDecimal budget, BigDecimal actual) {
		return budget.signum() == 0 ? null : actual.multiply(HUNDRED).divide(budget, 1, RoundingMode.HALF_UP);
	}

	public static boolean overBudget(BigDecimal budget, BigDecimal actual) {
		return actual.compareTo(budget) > 0;
	}

	/** The change from {@code previous} to {@code current} in %, one decimal; null when there was nothing before. */
	public static BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
		return previous.signum() == 0 ? null
				: current.subtract(previous).multiply(HUNDRED).divide(previous, 1, RoundingMode.HALF_UP);
	}

}
