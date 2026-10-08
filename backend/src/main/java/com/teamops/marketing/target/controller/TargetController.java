package com.teamops.marketing.target.controller;

import java.util.Set;

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
import com.teamops.marketing.common.TargetProgress.TargetStatus;
import com.teamops.marketing.target.dto.TargetDtos.CreateTarget;
import com.teamops.marketing.target.dto.TargetDtos.MonthlyTargets;
import com.teamops.marketing.target.dto.TargetDtos.SetMonthlyResult;
import com.teamops.marketing.target.dto.TargetDtos.SetMonthlyTargets;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.marketing.target.dto.TargetDtos.TargetTrend;
import com.teamops.marketing.target.dto.TargetDtos.TrendView;
import com.teamops.marketing.target.dto.TargetDtos.UpdateTarget;
import com.teamops.marketing.target.service.TargetService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Monthly marketing targets: TARGET_VIEW reads, TARGET_EDIT sets and changes them. Months default to the current
 * business month; closed months return 409 MONTH_LOCKED except to a Super Admin.
 */
@RestController
@RequestMapping("/api/marketing/targets")
@RequiredArgsConstructor
public class TargetController {

	private final TargetService targetService;

	/** The target performance table for the month, with a status summary and the types still without a target. */
	@GetMapping
	@PreAuthorize("hasAuthority('TARGET_VIEW')")
	public MonthlyTargets monthly(@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Set<TargetStatus> status,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return targetService.monthly(targetService.period(month, year), ownerId, status, viewer);
	}

	/** Targets against actuals by MONTH (12 months), QUARTER (4 quarters) or YEAR (5 years) ending in {@code year}. */
	@GetMapping("/trend")
	@PreAuthorize("hasAuthority('TARGET_VIEW')")
	public TargetTrend trend(@RequestParam Long typeId, @RequestParam(defaultValue = "MONTH") TrendView view,
			@RequestParam(required = false) Integer year) {
		return targetService.trend(typeId, view, targetService.period(null, year).year());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('TARGET_VIEW')")
	public TargetItem get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return targetService.get(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('TARGET_EDIT')")
	public ResponseEntity<TargetItem> create(@Valid @RequestBody CreateTarget request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(targetService.create(request, actor, ClientInfo.from(http)));
	}

	/** Sets several types' targets for one month, all or nothing (409 TARGET_EXISTS if any is already set). */
	@PostMapping("/monthly")
	@PreAuthorize("hasAuthority('TARGET_EDIT')")
	public ResponseEntity<SetMonthlyResult> setMonthly(@Valid @RequestBody SetMonthlyTargets request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(targetService.setMonthly(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('TARGET_EDIT')")
	public TargetItem update(@PathVariable Long id, @Valid @RequestBody UpdateTarget request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return targetService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('TARGET_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		targetService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
