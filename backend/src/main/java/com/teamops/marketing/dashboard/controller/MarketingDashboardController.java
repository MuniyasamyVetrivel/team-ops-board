package com.teamops.marketing.dashboard.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.Dashboard;
import com.teamops.marketing.dashboard.service.MarketingDashboardService;

import lombok.RequiredArgsConstructor;

/**
 * The Digital Marketing executive dashboard: MARKETING_VIEW opens it, and each section appears only with its module's
 * view permission. Month/year default to the current business month; {@code ownerId} narrows the figures to one
 * owner; {@code months} sets the trend length.
 */
@RestController
@RequestMapping("/api/marketing/dashboard")
@RequiredArgsConstructor
public class MarketingDashboardController {

	private final MarketingDashboardService dashboardService;

	@GetMapping
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public Dashboard dashboard(@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
			@RequestParam(required = false) Long ownerId, @RequestParam(required = false) Integer months,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return dashboardService.dashboard(dashboardService.period(month, year), ownerId, months, viewer);
	}

}
