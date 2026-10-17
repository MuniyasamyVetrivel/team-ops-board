package com.teamops.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.finance.dto.FinanceDtos.BudgetSummary;
import com.teamops.finance.dto.FinanceDtos.FinanceDashboard;
import com.teamops.finance.dto.FinanceDtos.LicenceDto;
import com.teamops.finance.dto.FinanceDtos.MonthSpend;
import com.teamops.finance.entity.BillingCycle;
import com.teamops.finance.entity.ExpenseCategory;
import com.teamops.finance.entity.LicenceStatus;
import com.teamops.finance.repository.FinanceExpenseRepository;
import com.teamops.finance.repository.FinanceExpenseRepository.CategoryTotal;
import com.teamops.finance.repository.FinanceExpenseRepository.MonthTotal;

/** The dashboard: a zero-filled 12-month trend, the month against the last, renewals, under-used and annual costs. */
class FinanceDashboardServiceTest {

	private final FinanceExpenseRepository expenses = mock(FinanceExpenseRepository.class);

	private final LicenceService licences = mock(LicenceService.class);

	private final BudgetService budgets = mock(BudgetService.class);

	private final FinanceDashboardService service = new FinanceDashboardService(expenses, licences, budgets,
			mock(DepartmentRepository.class),
			new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"));

	@Test
	void theMonthIsComparedWithTheLastAndTheTrendCoversTwelveMonths() {
		when(expenses.totalsByMonth(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 10, 31)))
			.thenReturn(List.of(month(2026, 9, "400.00"), month(2026, 10, "500.00"), month(2026, 1, "50.00")));
		when(expenses.totalsByCategory(any(), any())).thenReturn(List.of(category(ExpenseCategory.TRAVEL, "300.00", 2),
				category(ExpenseCategory.SOFTWARE, "200.00", 3)));
		when(budgets.summary(2026, 10)).thenReturn(new BudgetSummary(2026, 10, List.of(), new BigDecimal("900.00"),
				new BigDecimal("500.00"), new BigDecimal("500.00"), 1));
		when(licences.inUse()).thenReturn(List.of());

		FinanceDashboard dashboard = service.dashboard(null, null);

		assertThat(dashboard.year()).isEqualTo(2026);
		assertThat(dashboard.month()).isEqualTo(10);
		assertThat(dashboard.monthSpendAud()).isEqualByComparingTo("500.00");
		assertThat(dashboard.previousMonthSpendAud()).isEqualByComparingTo("400.00");
		assertThat(dashboard.changePercent()).isEqualByComparingTo("25.0");
		assertThat(dashboard.expenseCount()).isEqualTo(5);
		assertThat(dashboard.trend()).hasSize(12)
			.first()
			.isEqualTo(new MonthSpend(2025, 11, new BigDecimal("0.00")));
		assertThat(dashboard.trend()).extracting(MonthSpend::month).endsWith(9, 10);
		assertThat(dashboard.byCategory()).extracting(c -> c.category()).containsExactly(ExpenseCategory.TRAVEL,
				ExpenseCategory.SOFTWARE);
		assertThat(dashboard.monthBudgetAud()).isEqualByComparingTo("900.00");
		assertThat(dashboard.overBudgetDepartments()).isEqualTo(1);
	}

	@Test
	void renewalsUnderUseAndTheAnnualisedCostComeFromTheLicencesInUse() {
		when(expenses.totalsByMonth(any(), any())).thenReturn(List.of());
		when(expenses.totalsByCategory(any(), any())).thenReturn(List.of());
		when(budgets.summary(2026, 10)).thenReturn(new BudgetSummary(2026, 10, List.of(), BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, 0));
		when(licences.inUse()).thenReturn(List.of(licence("Jira", 5L, true, "40.0", "1200.00", "1200.00"),
				licence("Slack", 40L, false, "90.0", "600.00", "300.00"), licence("Figma", null, true, "20.0", "999.00", null),
				licence("Laptop setup", 2L, false, null, null, null)));

		FinanceDashboard dashboard = service.dashboard(2026, 10);

		assertThat(dashboard.upcomingRenewalCount()).isEqualTo(2);
		assertThat(dashboard.upcomingRenewals()).extracting(LicenceDto::name).containsExactly("Laptop setup", "Jira");
		assertThat(dashboard.underUsedCount()).isEqualTo(2);
		assertThat(dashboard.underUsedLicences()).extracting(LicenceDto::name).containsExactly("Figma", "Jira");
		assertThat(dashboard.annualisedSoftwareCostAud()).isEqualByComparingTo("1500.00");
		assertThat(dashboard.licencesWithoutRate()).isEqualTo(1);
		assertThat(dashboard.changePercent()).isNull();
	}

	private static LicenceDto licence(String name, Long daysUntilRenewal, boolean underUsed, String utilisation,
			String annualCost, String annualCostAud) {
		return new LicenceDto(1L, name, null, null, null, null, 10, 4, 6, utilisation == null ? null : new BigDecimal(utilisation),
				underUsed, false, List.of(), BillingCycle.YEARLY, null, "AUD",
				annualCost == null ? null : new BigDecimal(annualCost), annualCostAud == null ? null : new BigDecimal(annualCostAud),
				null, null, null, daysUntilRenewal, null, null, LicenceStatus.ACTIVE, null, 0, null, null);
	}

	private static MonthTotal month(int year, int month, String total) {
		return new MonthTotal() {
			@Override
			public int getYear() {
				return year;
			}

			@Override
			public int getMonth() {
				return month;
			}

			@Override
			public BigDecimal getTotal() {
				return new BigDecimal(total);
			}
		};
	}

	private static CategoryTotal category(ExpenseCategory category, String total, long count) {
		return new CategoryTotal() {
			@Override
			public ExpenseCategory getCategory() {
				return category;
			}

			@Override
			public BigDecimal getTotal() {
				return new BigDecimal(total);
			}

			@Override
			public long getCount() {
				return count;
			}
		};
	}

}
