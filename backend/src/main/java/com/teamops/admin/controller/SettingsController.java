package com.teamops.admin.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.admin.dto.SettingDtos.SettingItem;
import com.teamops.admin.dto.SettingDtos.UpdateSetting;
import com.teamops.admin.service.SettingsAdminService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** System settings (workload, capacity, SLA warning, marketing threshold, upload size). SETTINGS_MANAGE only. */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
public class SettingsController {

	private final SettingsAdminService settingsAdminService;

	@GetMapping
	public List<SettingItem> list() {
		return settingsAdminService.list();
	}

	@PutMapping("/{key}")
	public SettingItem update(@PathVariable String key, @Valid @RequestBody UpdateSetting request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return settingsAdminService.update(key, request, actor, ClientInfo.from(http));
	}

}
