package com.teamops.finance.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.dto.FinanceDtos.ExpenseDto;
import com.teamops.finance.dto.FinanceDtos.SaveExpense;
import com.teamops.finance.entity.ExpenseCategory;
import com.teamops.finance.service.ExpenseService;
import com.teamops.finance.service.ExpenseService.ExpenseFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Expenses and their receipts. FINANCE_VIEW reads and downloads, FINANCE_EDIT changes and uploads; the module switch
 * applies to every endpoint.
 */
@RestController
@RequestMapping(FinanceModule.API + "/expenses")
@PreAuthorize("hasAuthority('FINANCE_VIEW')")
@RequiredArgsConstructor
public class ExpenseController {

	static final Map<String, List<String>> SORTS = Map.of("date", List.of("expenseDate", "id"), "amount",
			List.of("amountAud", "id"), "category", List.of("category", "expenseDate", "id"), "updated",
			List.of("updatedAt", "id"));

	private final ExpenseService expenseService;

	@GetMapping
	public PageResponse<ExpenseDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) ExpenseCategory category, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Long vendorId, @RequestParam(required = false) Long approvalId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort) {
		return expenseService.search(new ExpenseFilter(search, category, departmentId, vendorId, approvalId, from, to),
				PageRequests.of(page, size, sort, SORTS, "date,desc"));
	}

	/** CSV with the stored AUD rate and amount of each expense. */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) ExpenseCategory category, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Long vendorId, @RequestParam(required = false) Long approvalId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
		return FinanceDownloads.csv("expenses.csv",
				expenseService.export(new ExpenseFilter(search, category, departmentId, vendorId, approvalId, from, to)));
	}

	@GetMapping("/{id}")
	public ExpenseDto get(@PathVariable Long id) {
		return expenseService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ResponseEntity<ExpenseDto> create(@Valid @RequestBody SaveExpense request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ExpenseDto update(@PathVariable Long id, @Valid @RequestBody SaveExpense request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return expenseService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		expenseService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	/** Attaches the receipt, replacing any earlier one. */
	@PostMapping(path = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ExpenseDto attachReceipt(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return expenseService.attachReceipt(id, file, actor, ClientInfo.from(http));
	}

	/** Always served as a download. */
	@GetMapping("/{id}/receipt")
	public ResponseEntity<Resource> receipt(@PathVariable Long id) {
		return FinanceDownloads.file(expenseService.receipt(id));
	}

	@DeleteMapping("/{id}/receipt")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ExpenseDto removeReceipt(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return expenseService.removeReceipt(id, actor, ClientInfo.from(http));
	}

}
