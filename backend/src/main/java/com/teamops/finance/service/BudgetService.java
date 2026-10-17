package com.teamops.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.finance.dto.FinanceDtos.BudgetDto;
import com.teamops.finance.dto.FinanceDtos.BudgetLine;
import com.teamops.finance.dto.FinanceDtos.BudgetSummary;
import com.teamops.finance.dto.FinanceDtos.SaveBudget;
import com.teamops.finance.entity.FinanceBudget;
import com.teamops.finance.repository.FinanceBudgetRepository;
import com.teamops.finance.repository.FinanceExpenseRepository;
import com.teamops.finance.repository.FinanceExpenseRepository.DepartmentTotal;
import com.teamops.recyclebin.service.RecycleBinService;

import lombok.RequiredArgsConstructor;

/**
 * Department budgets per month, in AUD (Phase X8), one per department and month (including budgets in the recycle
 * bin). Actual spend is the month's expenses in AUD (their stored AUD amounts), counted by expense date; remaining,
 * used % and over-budget come from {@link BudgetMath}, never stored. Changes are audited; amounts are finance amounts.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

	public static final String ENTITY = "FINANCE_BUDGET";

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final FinanceBudgetRepository repository;

	private final FinanceExpenseRepository expenses;

	private final DepartmentRepository departments;

	private final AuditService auditService;

	private final RecycleBinService recycleBin;

	public record BudgetFilter(Integer year, Integer month, Long departmentId) {
	}

	@Transactional(readOnly = true)
	public PageResponse<BudgetDto> search(BudgetFilter filter, Pageable pageable) {
		var page = repository.findAll(spec(filter), pageable);
		Map<YearMonth, Map<Long, BigDecimal>> actuals = new HashMap<>();
		return PageResponse.of(page.map(budget -> toDto(budget, actuals.computeIfAbsent(
				YearMonth.of(budget.getYear(), budget.getMonth()), this::actualsByDepartment))));
	}

	@Transactional(readOnly = true)
	public BudgetDto get(Long id) {
		FinanceBudget budget = load(id);
		return toDto(budget, actualsByDepartment(YearMonth.of(budget.getYear(), budget.getMonth())));
	}

	/**
	 * A month's budget against actual: every department with a budget or with spend, the spend no department was given
	 * (a line without department), and the totals. Lines with the largest spend come first.
	 */
	@Transactional(readOnly = true)
	public BudgetSummary summary(int year, int month) {
		YearMonth period = period(year, month);
		Map<Long, BigDecimal> actuals = actualsByDepartment(period);
		Map<Long, FinanceBudget> budgets = new LinkedHashMap<>();
		repository.findByYearAndMonth(year, month).forEach(b -> budgets.put(b.getDepartment().getId(), b));
		Map<Long, Department> named = new HashMap<>();
		departments.findAllById(actuals.keySet().stream().filter(Objects::nonNull).toList())
			.forEach(d -> named.put(d.getId(), d));
		budgets.values().forEach(b -> named.put(b.getDepartment().getId(), b.getDepartment()));

		List<BudgetLine> lines = new ArrayList<>();
		BigDecimal totalBudget = ZERO;
		BigDecimal totalActualBudgeted = ZERO;
		long over = 0;
		for (Department department : named.values()) {
			FinanceBudget budget = budgets.get(department.getId());
			BigDecimal actual = actuals.getOrDefault(department.getId(), ZERO);
			BudgetLine line = line(DepartmentSummary.of(department), budget, actual);
			lines.add(line);
			if (budget != null) {
				totalBudget = totalBudget.add(budget.getAmount());
				totalActualBudgeted = totalActualBudgeted.add(actual);
				over += line.overBudget() ? 1 : 0;
			}
		}
		if (actuals.containsKey(null)) {
			lines.add(line(null, null, actuals.get(null)));
		}
		lines.sort(Comparator.comparing(BudgetLine::actual).reversed()
			.thenComparing(l -> l.department() == null ? "~" : l.department().name()));
		BigDecimal totalActual = actuals.values().stream().reduce(ZERO, BigDecimal::add);
		return new BudgetSummary(year, month, lines, totalBudget, totalActual, totalActualBudgeted, over);
	}

	@Transactional
	public BudgetDto create(SaveBudget request, AuthenticatedUser actor, ClientInfo info) {
		FinanceBudget budget = new FinanceBudget();
		apply(budget, request);
		FinanceBudget saved = repository.saveAndFlush(budget);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("departmentId", saved.getDepartment().getId());
		details.put("department", saved.getDepartment().getName());
		details.put("year", saved.getYear());
		details.put("month", saved.getMonth());
		details.put("amount", saved.getAmount());
		auditService.record(AuditAction.BUDGET_CREATED, actor.id(), ENTITY, saved.getId(), details, info);
		return get(saved.getId());
	}

	/** The amount and notes; the department and month stay (delete and re-create to move a budget). */
	@Transactional
	public BudgetDto update(Long id, SaveBudget request, AuthenticatedUser actor, ClientInfo info) {
		FinanceBudget budget = load(id);
		if (request.version() == null || !Objects.equals(budget.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this budget just now. Reload and try again.");
		}
		Map<String, Object> before = snapshot(budget);
		apply(budget, request);
		AuditChanges changes = new AuditChanges();
		snapshot(budget).forEach((field, to) -> changes.track(field, before.get(field), to));
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.BUDGET_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), info);
		}
		return get(id);
	}

	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo info) {
		FinanceBudget budget = load(id);
		recycleBin.moveToBin(budget, actor);
		auditService.record(AuditAction.BUDGET_DELETED, actor.id(), ENTITY, id, Map.of("department",
				budget.getDepartment().getName(), "year", budget.getYear(), "month", budget.getMonth()), info);
	}

	/** The month's AUD spend by department id (null for expenses without a department). */
	Map<Long, BigDecimal> actualsByDepartment(YearMonth period) {
		Map<Long, BigDecimal> actuals = new HashMap<>();
		for (DepartmentTotal total : expenses.totalsByDepartment(period.atDay(1), period.atEndOfMonth())) {
			actuals.put(total.getDepartmentId(), total.getTotal().setScale(2, RoundingMode.HALF_UP));
		}
		return actuals;
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private void apply(FinanceBudget budget, SaveBudget request) {
		YearMonth period = period(request.year(), request.month());
		if (budget.getId() == null) {
			Department department = departments.findById(request.departmentId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
			if (repository.existsByDepartmentIdAndYearAndMonth(department.getId(), period.getYear(), period.getMonthValue())) {
				throw ApiException.conflict("BUDGET_EXISTS",
						department.getName() + " already has a budget for " + period + ". Edit that one.");
			}
			recycleBin.requireNotInBin(FinanceBudget.class, Map.of("department_id", department.getId(), "budget_year",
					period.getYear(), "budget_month", period.getMonthValue()), "The " + period + " budget of "
							+ department.getName());
			budget.setDepartment(department);
			budget.setYear(period.getYear());
			budget.setMonth(period.getMonthValue());
		}
		else if (!budget.getDepartment().getId().equals(request.departmentId()) || budget.getYear() != period.getYear()
				|| budget.getMonth() != period.getMonthValue()) {
			throw ApiException.badRequest("BUDGET_PERIOD_FIXED",
					"A budget's department and month cannot change. Delete it and add a new one.");
		}
		budget.setAmount(request.amount().setScale(2, RoundingMode.UNNECESSARY));
		budget.setNotes(FinanceText.trimToNull(request.notes()));
	}

	static YearMonth period(int year, int month) {
		if (month < 1 || month > 12 || year < 2000 || year > 2100) {
			throw ApiException.badRequest("INVALID_PERIOD", "Give a month from 1 to 12 and a year from 2000 to 2100");
		}
		return YearMonth.of(year, month);
	}

	private static Map<String, Object> snapshot(FinanceBudget b) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("amount", b.getAmount());
		values.put("notes", b.getNotes());
		return values;
	}

	private static BudgetLine line(DepartmentSummary department, FinanceBudget budget, BigDecimal actual) {
		if (budget == null) {
			return new BudgetLine(department, null, null, actual, null, null, false, null);
		}
		BigDecimal amount = budget.getAmount();
		return new BudgetLine(department, budget.getId(), amount, actual, BudgetMath.remaining(amount, actual),
				BudgetMath.usedPercent(amount, actual), BudgetMath.overBudget(amount, actual),
				BudgetMath.overBy(amount, actual));
	}

	private static BudgetDto toDto(FinanceBudget b, Map<Long, BigDecimal> actuals) {
		BigDecimal actual = actuals.getOrDefault(b.getDepartment().getId(), ZERO);
		BigDecimal amount = b.getAmount();
		return new BudgetDto(b.getId(), DepartmentSummary.of(b.getDepartment()), b.getYear(), b.getMonth(), amount, actual,
				BudgetMath.remaining(amount, actual), BudgetMath.usedPercent(amount, actual),
				BudgetMath.overBudget(amount, actual), BudgetMath.overBy(amount, actual), b.getNotes(), b.getVersion(),
				b.getCreatedAt(), b.getUpdatedAt());
	}

	private FinanceBudget load(Long id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("BUDGET_NOT_FOUND", "Budget not found"));
	}

	private static Specification<FinanceBudget> spec(BudgetFilter f) {
		Specification<FinanceBudget> spec = (root, query, cb) -> cb.conjunction();
		if (f.year() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("year"), f.year()));
		}
		if (f.month() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("month"), f.month()));
		}
		if (f.departmentId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("department").get("id"), f.departmentId()));
		}
		return spec;
	}

}
