package com.teamops.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.finance.dto.FinanceDtos.BudgetSummary;
import com.teamops.finance.dto.FinanceDtos.CategorySpend;
import com.teamops.finance.dto.FinanceDtos.DepartmentSpend;
import com.teamops.finance.dto.FinanceDtos.FinanceDashboard;
import com.teamops.finance.dto.FinanceDtos.LicenceDto;
import com.teamops.finance.dto.FinanceDtos.MonthSpend;
import com.teamops.finance.repository.FinanceExpenseRepository;
import com.teamops.finance.repository.FinanceExpenseRepository.CategoryTotal;
import com.teamops.finance.repository.FinanceExpenseRepository.DepartmentTotal;
import com.teamops.finance.repository.FinanceExpenseRepository.MonthTotal;

import lombok.RequiredArgsConstructor;

/**
 * The Finance Tracker dashboard (brief X8) for a month, in AUD: the month's spend against the previous month, the
 * 12-month trend, spend by department and category, the month's budgets, renewals in the next 30 days, licences with
 * seats used below 70% and the annualised software cost. Built from three aggregate expense queries and one pass over
 * the licences in use; nothing is stored.
 */
@Service
@RequiredArgsConstructor
public class FinanceDashboardService {

	/** Brief: "upcoming renewals (30 days)". */
	public static final int RENEWAL_WINDOW_DAYS = 30;

	static final int TREND_MONTHS = 12;

	static final int LIST_LIMIT = 10;

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final FinanceExpenseRepository expenses;

	private final LicenceService licences;

	private final BudgetService budgets;

	private final DepartmentRepository departments;

	private final BusinessCalendar calendar;

	/** The month to show: this month (business calendar) unless both are given. */
	@Transactional(readOnly = true)
	public FinanceDashboard dashboard(Integer year, Integer month) {
		LocalDate today = calendar.today();
		YearMonth period = year == null || month == null ? YearMonth.from(today) : BudgetService.period(year, month);
		LocalDate from = period.atDay(1);
		LocalDate to = period.atEndOfMonth();

		List<MonthSpend> trend = trend(period);
		BigDecimal monthSpend = trend.getLast().totalAud();
		BigDecimal previousSpend = trend.get(trend.size() - 2).totalAud();

		List<CategoryTotal> categoryTotals = expenses.totalsByCategory(from, to);
		List<CategorySpend> byCategory = categoryTotals.stream()
			.map(t -> new CategorySpend(t.getCategory(), money(t.getTotal()), t.getCount()))
			.sorted(Comparator.comparing(CategorySpend::totalAud).reversed())
			.toList();
		long expenseCount = categoryTotals.stream().mapToLong(CategoryTotal::getCount).sum();

		BudgetSummary budget = budgets.summary(period.getYear(), period.getMonthValue());

		List<LicenceDto> inUse = licences.inUse();
		List<LicenceDto> renewing = inUse.stream()
			.filter(l -> l.daysUntilRenewal() != null && l.daysUntilRenewal() <= RENEWAL_WINDOW_DAYS)
			.sorted(Comparator.comparing(LicenceDto::daysUntilRenewal).thenComparing(LicenceDto::name))
			.toList();
		List<LicenceDto> underUsed = inUse.stream()
			.filter(LicenceDto::underUsed)
			.sorted(Comparator.comparing(LicenceDto::utilisationPercent).thenComparing(LicenceDto::name))
			.toList();
		BigDecimal annualised = ZERO;
		long withoutRate = 0;
		for (LicenceDto licence : inUse) {
			// One-off licences have no annual cost and never count towards it.
			if (licence.annualCost() == null) {
				continue;
			}
			if (licence.annualCostAud() == null) {
				withoutRate++;
			}
			else {
				annualised = annualised.add(licence.annualCostAud());
			}
		}

		return new FinanceDashboard(today, period.getYear(), period.getMonthValue(), monthSpend, previousSpend,
				BudgetMath.changePercent(monthSpend, previousSpend), expenseCount, trend, byDepartment(from, to),
				byCategory, budget.totalBudget(), budget.overBudgetDepartments(), renewing.size(),
				renewing.stream().limit(LIST_LIMIT).toList(), underUsed.size(), underUsed.stream().limit(LIST_LIMIT).toList(),
				annualised, withoutRate);
	}

	/** The 12 months up to and including {@code last}, oldest first, months without expenses at 0. */
	private List<MonthSpend> trend(YearMonth last) {
		YearMonth first = last.minusMonths(TREND_MONTHS - 1);
		Map<YearMonth, BigDecimal> totals = new HashMap<>();
		for (MonthTotal total : expenses.totalsByMonth(first.atDay(1), last.atEndOfMonth())) {
			totals.put(YearMonth.of(total.getYear(), total.getMonth()), money(total.getTotal()));
		}
		List<MonthSpend> trend = new ArrayList<>(TREND_MONTHS);
		for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
			trend.add(new MonthSpend(month.getYear(), month.getMonthValue(), totals.getOrDefault(month, ZERO)));
		}
		return trend;
	}

	/** Largest first; spend without a department under a null department. */
	private List<DepartmentSpend> byDepartment(LocalDate from, LocalDate to) {
		List<DepartmentTotal> totals = expenses.totalsByDepartment(from, to);
		Map<Long, Department> named = new HashMap<>();
		departments.findAllById(totals.stream().map(DepartmentTotal::getDepartmentId).filter(Objects::nonNull).toList())
			.forEach(d -> named.put(d.getId(), d));
		return totals.stream()
			.map(t -> new DepartmentSpend(t.getDepartmentId() == null || !named.containsKey(t.getDepartmentId()) ? null
					: DepartmentSummary.of(named.get(t.getDepartmentId())), money(t.getTotal())))
			.sorted(Comparator.comparing(DepartmentSpend::totalAud).reversed())
			.toList();
	}

	private static BigDecimal money(BigDecimal value) {
		return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
	}

}
