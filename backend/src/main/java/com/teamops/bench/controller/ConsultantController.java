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
import com.teamops.bench.dto.BenchDtos.ConsultantDto;
import com.teamops.bench.dto.BenchDtos.ConsultantEmployee;
import com.teamops.bench.dto.BenchDtos.SaveConsultant;
import com.teamops.bench.service.ConsultantService;
import com.teamops.bench.service.ConsultantService.ConsultantFilter;
import com.teamops.bench.service.ConsultantStatus;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Bench Management consultants. BENCH_VIEW reads, BENCH_EDIT changes; the module switch applies to every endpoint
 * ({@code ModuleAccessInterceptor}). CSV import runs through {@code /api/modules/bench/imports/consultants}.
 */
@RestController
@RequestMapping(BenchModule.API + "/consultants")
@PreAuthorize("hasAuthority('BENCH_VIEW')")
@RequiredArgsConstructor
public class ConsultantController {

	static final Map<String, List<String>> SORTS = Map.of("name", List.of("employee.firstName", "employee.lastName", "id"),
			"code", List.of("employee.employeeCode", "id"), "available", List.of("availableFrom", "id"), "experience",
			List.of("experienceYears", "id"), "updated", List.of("updatedAt", "id"));

	private final ConsultantService consultantService;

	@GetMapping
	public PageResponse<ConsultantDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) ConsultantStatus status, @RequestParam(required = false) String skill,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort) {
		return consultantService.search(new ConsultantFilter(search, status, skill),
				PageRequests.of(page, size, sort, SORTS, "name,asc"));
	}

	/** Active employees who are not consultants yet (the "add consultant" picker). */
	@GetMapping("/candidates")
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public List<ConsultantEmployee> candidates(@RequestParam(required = false) String search) {
		return consultantService.candidates(search);
	}

	/** CSV in the import's columns. */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) ConsultantStatus status, @RequestParam(required = false) String skill) {
		return BenchCsv.download("consultants.csv", consultantService.export(new ConsultantFilter(search, status, skill)));
	}

	@GetMapping("/{id}")
	public ConsultantDto get(@PathVariable Long id) {
		return consultantService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ResponseEntity<ConsultantDto> create(@Valid @RequestBody SaveConsultant request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(consultantService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ConsultantDto update(@PathVariable Long id, @Valid @RequestBody SaveConsultant request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return consultantService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		consultantService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
