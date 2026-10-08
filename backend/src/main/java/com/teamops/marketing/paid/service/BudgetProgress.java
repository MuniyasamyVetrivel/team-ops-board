package com.teamops.marketing.paid.service;

import java.math.BigDecimal;

import com.teamops.marketing.common.MarketingMath;

/**
 * Budget against spend to date (brief section 42). Remaining = budget − spent and goes negative when the campaign
 * overspends (not clamped, so the overspend shows).
 *
 * @param usedPct spent ÷ budget × 100 (₹42,000 of ₹50,000 = 84.00)
 */
public record BudgetProgress(BigDecimal budget, BigDecimal spent, BigDecimal remaining, BigDecimal usedPct,
		boolean overBudget) {

	public static BudgetProgress of(BigDecimal budget, BigDecimal spent) {
		BigDecimal remaining = MarketingMath.remainingBudget(budget, spent);
		return new BudgetProgress(budget, spent, remaining, MarketingMath.percent(spent, budget),
				remaining != null && remaining.signum() < 0);
	}

}
