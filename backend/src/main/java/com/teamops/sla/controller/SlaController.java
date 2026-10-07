package com.teamops.sla.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.sla.dto.SlaDtos.PolicyResponse;
import com.teamops.sla.dto.SlaDtos.Summary;
import com.teamops.sla.dto.SlaDtos.UpdatePolicy;
import com.teamops.sla.service.SlaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** SLA policies and compliance. Reading needs TICKET_VIEW (scoped to visible tickets); changing needs SLA_MANAGE. */
@RestController
@RequestMapping("/api/sla")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TICKET_VIEW')")
public class SlaController {

	private final SlaService slaService;

	@GetMapping("/policies")
	public List<PolicyResponse> policies() {
		return slaService.policies();
	}

	@PutMapping("/policies/{id}")
	@PreAuthorize("hasAuthority('SLA_MANAGE')")
	public PolicyResponse updatePolicy(@PathVariable Long id, @Valid @RequestBody UpdatePolicy request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return slaService.updatePolicy(id, request, actor, ClientInfo.from(http));
	}

	@GetMapping("/summary")
	public Summary summary(@RequestParam(defaultValue = "30") int days,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return slaService.summary(days, actor);
	}

}
