package com.teamops.bench.controller;

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
import com.teamops.bench.dto.BenchDtos.EngagementDto;
import com.teamops.bench.dto.BenchDtos.SaveEngagement;
import com.teamops.bench.service.EngagementService;
import com.teamops.bench.service.EngagementService.EngagementFilter;
import com.teamops.bench.service.PeriodStatus;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Bench Management client engagements. BENCH_VIEW reads, BENCH_EDIT changes; the module switch applies to every
 * endpoint.
 */
@RestController
@RequestMapping(BenchModule.API + "/engagements")
@PreAuthorize("hasAuthority('BENCH_VIEW')")
@RequiredArgsConstructor
public class EngagementController {

	static final Map<String, List<String>> SORTS = Map.of("name", List.of("name", "id"), "client",
			List.of("client.name", "name", "id"), "start", List.of("startDate", "id"), "end", List.of("endDate", "id"),
			"updated", List.of("updatedAt", "id"));

	private final EngagementService engagementService;

	@GetMapping
	public PageResponse<EngagementDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long clientId, @RequestParam(required = false) PeriodStatus status,
			@RequestParam(required = false) Long accountManagerId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort) {
		return engagementService.search(new EngagementFilter(search, clientId, status, accountManagerId),
				PageRequests.of(page, size, sort, SORTS, "start,desc"));
	}

	@GetMapping("/{id}")
	public EngagementDto get(@PathVariable Long id) {
		return engagementService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ResponseEntity<EngagementDto> create(@Valid @RequestBody SaveEngagement request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(engagementService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public EngagementDto update(@PathVariable Long id, @Valid @RequestBody SaveEngagement request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return engagementService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		engagementService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
