package com.teamops.module.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.module.dto.ModuleDtos.Tool;
import com.teamops.module.service.ModuleAccessService;

import lombok.RequiredArgsConstructor;

/**
 * The sidebar's "Department tools": the modules switched on for the signed-in user's departments and permitted to
 * them (any signed-in user; usually an empty list).
 */
@RestController
@RequiredArgsConstructor
public class DepartmentToolsController {

	private final ModuleAccessService moduleAccessService;

	@GetMapping("/api/department-tools")
	public List<Tool> mine(@AuthenticationPrincipal AuthenticatedUser user) {
		return moduleAccessService.toolsFor(user);
	}

}
