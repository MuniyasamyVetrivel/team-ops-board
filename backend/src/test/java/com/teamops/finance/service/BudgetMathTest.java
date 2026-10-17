package com.teamops.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Budget against actual, computed and never stored; division by zero gives null. */
class BudgetMathTest {

	private static final BigDecimal BUDGET = new BigDecimal("1000.00");

	@Test
	void underBudgetLeavesTheRestRemaining() {
		BigDecimal actual = new BigDecimal("800.00");
		assertThat(BudgetMath.remaining(BUDGET, actual)).isEqualByComparingTo("200.00");
		assertThat(BudgetMath.overBy(BUDGET, actual)).isEqualByComparingTo("0.00");
		assertThat(BudgetMath.usedPercent(BUDGET, actual)).isEqualByComparingTo("80.0");
		assertThat(BudgetMath.overBudget(BUDGET, actual)).isFalse();
	}

	@Test
	void overBudgetSaysByHowMuch() {
		BigDecimal actual = new BigDecimal("1100.00");
		assertThat(BudgetMath.remaining(BUDGET, actual)).isEqualByComparingTo("0.00");
		assertThat(BudgetMath.overBy(BUDGET, actual)).isEqualByComparingTo("100.00");
		assertThat(BudgetMath.usedPercent(BUDGET, actual)).isEqualByComparingTo("110.0");
		assertThat(BudgetMath.overBudget(BUDGET, actual)).isTrue();
		assertThat(BudgetMath.overBudget(BUDGET, BUDGET)).isFalse();
	}

	@Test
	void aZeroBudgetHasNoUsedPercentAndNoPreviousMonthNoChange() {
		assertThat(BudgetMath.usedPercent(BigDecimal.ZERO, BigDecimal.TEN)).isNull();
		assertThat(BudgetMath.changePercent(new BigDecimal("150"), new BigDecimal("100"))).isEqualByComparingTo("50.0");
		assertThat(BudgetMath.changePercent(new BigDecimal("50"), new BigDecimal("200"))).isEqualByComparingTo("-75.0");
		assertThat(BudgetMath.changePercent(BigDecimal.TEN, BigDecimal.ZERO)).isNull();
	}

}
