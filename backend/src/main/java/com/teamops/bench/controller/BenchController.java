package com.teamops.bench.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.bench.BenchModule;
import com.teamops.bench.dto.BenchDtos.BenchDashboard;
import com.teamops.bench.dto.BenchDtos.BenchEntry;
import com.teamops.bench.service.BenchService;
import com.teamops.bench.service.BenchService.BenchFilter;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * The bench (consultants below 100% today) and the Bench Management dashboard. BENCH_VIEW, behind the module switch.
 */
@RestController
@RequestMapping(BenchModule.API)
@PreAuthorize("hasAuthority('BENCH_VIEW')")
@RequiredArgsConstructor
public class BenchController {

	static final Map<String, List<String>> SORTS = Map.of("days", List.of("daysOnBench"), "name", List.of("name"),
			"allocated", List.of("allocatedPercent"));

	private final BenchService benchService;

	@GetMapping("/bench")
	public PageResponse<BenchEntry> bench(@RequestParam(required = false) String search,
			@RequestParam(required = false) String skill, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort) {
		return benchService.bench(new BenchFilter(search, skill), PageRequests.of(page, size, sort, SORTS, "days,desc"));
	}

	@GetMapping("/dashboard")
	public BenchDashboard dashboard() {
		return benchService.dashboard();
	}

}
