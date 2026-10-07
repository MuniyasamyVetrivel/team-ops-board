package com.teamops.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/** Home dashboard. The response is scoped to the viewer by {@link DashboardService}. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
public class DashboardController {

	private final DashboardService dashboardService;

	@GetMapping
	public DashboardDtos.Response dashboard(@AuthenticationPrincipal AuthenticatedUser actor) {
		return dashboardService.dashboard(actor);
	}

}
