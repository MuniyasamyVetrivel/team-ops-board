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
import com.teamops.finance.dto.FinanceDtos.LicenceDto;
import com.teamops.finance.dto.FinanceDtos.SaveLicence;
import com.teamops.finance.entity.BillingCycle;
import com.teamops.finance.entity.LicenceStatus;
import com.teamops.finance.service.LicenceService;
import com.teamops.finance.service.LicenceService.LicenceFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Tools and licences. FINANCE_VIEW reads, FINANCE_EDIT changes; the module switch applies to every endpoint
 * ({@code ModuleAccessInterceptor}). CSV import runs through {@code /api/modules/finance/imports/licences}.
 */
@RestController
@RequestMapping(FinanceModule.API + "/licences")
@PreAuthorize("hasAuthority('FINANCE_VIEW')")
@RequiredArgsConstructor
public class LicenceController {

	static final Map<String, List<String>> SORTS = Map.of("name", List.of("name", "id"), "vendor",
			List.of("vendor.name", "name", "id"), "renewal", List.of("renewalDate", "id"), "cost", List.of("cost", "id"),
			"seats", List.of("seatsPaid", "id"), "updated", List.of("updatedAt", "id"));

	private final LicenceService licenceService;

	@GetMapping
	public PageResponse<LicenceDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long vendorId, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) LicenceStatus status, @RequestParam(required = false) BillingCycle billingCycle,
			@RequestParam(required = false) Integer renewalWithinDays, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort) {
		return licenceService.search(
				new LicenceFilter(search, vendorId, departmentId, status, billingCycle, renewalWithinDays),
				PageRequests.of(page, size, sort, SORTS, "name,asc"));
	}

	/** CSV in the import's columns, with each licence's assigned users. */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long vendorId, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) LicenceStatus status, @RequestParam(required = false) BillingCycle billingCycle,
			@RequestParam(required = false) Integer renewalWithinDays) {
		return FinanceCsv.download("licences.csv", licenceService
			.export(new LicenceFilter(search, vendorId, departmentId, status, billingCycle, renewalWithinDays)));
	}

	@GetMapping("/{id}")
	public LicenceDto get(@PathVariable Long id) {
		return licenceService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ResponseEntity<LicenceDto> create(@Valid @RequestBody SaveLicence request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(licenceService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public LicenceDto update(@PathVariable Long id, @Valid @RequestBody SaveLicence request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return licenceService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		licenceService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
