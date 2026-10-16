package com.teamops.bench.controller;

import java.time.LocalDate;
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

import com.teamops.bench.BenchModule;
import com.teamops.bench.dto.BenchDtos.AllocationCheck;
import com.teamops.bench.dto.BenchDtos.AllocationDto;
import com.teamops.bench.dto.BenchDtos.AllocationSaved;
import com.teamops.bench.dto.BenchDtos.SaveAllocation;
import com.teamops.bench.service.AllocationService;
import com.teamops.bench.service.AllocationService.AllocationFilter;
import com.teamops.bench.service.PeriodStatus;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Bench Management allocations. BENCH_VIEW reads, BENCH_EDIT changes; the module switch applies to every endpoint.
 * Create and update answer the saved allocation with any stretches where the consultant goes above 100% (a warning).
 */
@RestController
@RequestMapping(BenchModule.API + "/allocations")
@PreAuthorize("hasAuthority('BENCH_VIEW')")
@RequiredArgsConstructor
public class AllocationController {

	static final Map<String, List<String>> SORTS = Map.of("start", List.of("startDate", "id"), "end",
			List.of("endDate", "id"), "consultant", List.of("consultant.employee.firstName",
					"consultant.employee.lastName", "id"),
			"percent", List.of("allocationPercent", "id"), "updated", List.of("updatedAt", "id"));

	private final AllocationService allocationService;

	@GetMapping
	public PageResponse<AllocationDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long consultantId, @RequestParam(required = false) Long engagementId,
			@RequestParam(required = false) Long clientId, @RequestParam(required = false) PeriodStatus status,
			@RequestParam(required = false) Boolean billable, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort) {
		return allocationService.search(
				new AllocationFilter(search, consultantId, engagementId, clientId, status, billable),
				PageRequests.of(page, size, sort, SORTS, "start,desc"));
	}

	/** Where an allocation like this would take the consultant above 100%; nothing is saved. */
	@GetMapping("/check")
	public AllocationCheck check(@RequestParam Long consultantId, @RequestParam LocalDate startDate,
			@RequestParam(required = false) LocalDate endDate,
			@RequestParam int allocationPercent, @RequestParam(required = false) Long excludeId) {
		return allocationService.check(consultantId, startDate, endDate, allocationPercent, excludeId);
	}

	@GetMapping("/{id}")
	public AllocationDto get(@PathVariable Long id) {
		return allocationService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ResponseEntity<AllocationSaved> create(@Valid @RequestBody SaveAllocation request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(allocationService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public AllocationSaved update(@PathVariable Long id, @Valid @RequestBody SaveAllocation request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return allocationService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		allocationService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
