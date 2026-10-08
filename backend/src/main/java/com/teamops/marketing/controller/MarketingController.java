package com.teamops.marketing.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.marketing.dto.MarketingDtos.Integrations;
import com.teamops.marketing.dto.MarketingDtos.MarketingContext;
import com.teamops.marketing.service.MarketingContextService;

import lombok.RequiredArgsConstructor;

/**
 * Shared Digital Marketing endpoints. Every {@code /api/marketing/**} route also requires MARKETING_VIEW in
 * {@code SecurityConfig}, so a new endpoint cannot be left open by a missing annotation.
 */
@RestController
@RequestMapping("/api/marketing")
@PreAuthorize("hasAuthority('MARKETING_VIEW')")
@RequiredArgsConstructor
public class MarketingController {

	private final MarketingContextService contextService;

	@GetMapping("/context")
	public MarketingContext context() {
		return contextService.context();
	}

	@GetMapping("/integrations")
	public Integrations integrations() {
		return contextService.integrations();
	}

}
