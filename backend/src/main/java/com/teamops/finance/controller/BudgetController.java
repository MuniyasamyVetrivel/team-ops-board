package com.teamops.finance.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.dto.FinanceDtos.BudgetDto;
import com.teamops.finance.dto.FinanceDtos.BudgetSummary;
import com.teamops.finance.dto.FinanceDtos.FinanceDashboard;
import com.teamops.finance.dto.FinanceDtos.SaveBudget;
import com.teamops.finance.service.BudgetService;
import com.teamops.finance.service.BudgetService.BudgetFilter;
import com.teamops.finance.service.FinanceDashboardService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Department budgets per month with actual against budget, and the Finance Tracker dashboard. FINANCE_VIEW reads,
 * FINANCE_EDIT changes; the module switch applies to every endpoint.
 */
@RestController
@RequestMapping(FinanceModule.API)
@PreAuthorize("hasAuthority('FINANCE_VIEW')")
@RequiredArgsConstructor
public class BudgetController {

	static final Map<String, List<String>> SORTS = Map.of("month", List.of("year", "month", "id"), "department",
			List.of("department.name", "year", "month", "id"), "amount", List.of("amount", "id"));

	private final BudgetService budgetService;

	private final FinanceDashboardService dashboardService;

	@GetMapping("/budgets")
	public PageResponse<BudgetDto> search(@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month, @RequestParam(required = false) Long departmentId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
			@RequestParam(required = false) String sort) {
		return budgetService.search(new BudgetFilter(year, month, departmentId),
				PageRequests.of(page, size, sort, SORTS, "department,asc"));
	}

	/** The month's budget against actual for every department with a budget or spend, with totals. */
	@GetMapping("/budgets/summary")
	public BudgetSummary summary(@RequestParam int year, @RequestParam int month) {
		return budgetService.summary(year, month);
	}

	@GetMapping("/budgets/{id}")
	public BudgetDto get(@PathVariable Long id) {
		return budgetService.get(id);
	}

	@PostMapping("/budgets")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ResponseEntity<BudgetDto> create(@Valid @RequestBody SaveBudget request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/budgets/{id}")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public BudgetDto update(@PathVariable Long id, @Valid @RequestBody SaveBudget request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return budgetService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/budgets/{id}")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		budgetService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	/** This month (business calendar) unless {@code year} and {@code month} are both given. */
	@GetMapping("/dashboard")
	public FinanceDashboard dashboard(@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month) {
		return dashboardService.dashboard(year, month);
	}

}
