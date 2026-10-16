package com.teamops.module.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.module.dto.ModuleDtos.ModuleSettings;
import com.teamops.module.dto.ModuleDtos.UpdateSwitch;
import com.teamops.module.service.ModuleAdminService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Department tools in Admin settings: switch each module on or off per department. MODULE_MANAGE only. */
@RestController
@RequestMapping("/api/admin/modules")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('MODULE_MANAGE')")
public class ModuleAdminController {

	private final ModuleAdminService moduleAdminService;

	@GetMapping
	public List<ModuleSettings> list() {
		return moduleAdminService.list();
	}

	@PutMapping("/{key}/departments/{departmentId}")
	public ModuleSettings setEnabled(@PathVariable String key, @PathVariable Long departmentId,
			@Valid @RequestBody UpdateSwitch request, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return moduleAdminService.setEnabled(key, departmentId, request.enabled(), actor, ClientInfo.from(http));
	}

}
