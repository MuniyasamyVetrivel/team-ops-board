package com.teamops.user.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.user.dto.PermissionResponse;
import com.teamops.user.dto.RoleResponse;
import com.teamops.user.service.AccessCatalogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('USER_MANAGE', 'PERMISSION_MANAGE')")
public class AccessCatalogController {

	private final AccessCatalogService accessCatalogService;

	@GetMapping("/roles")
	public List<RoleResponse> roles() {
		return accessCatalogService.roles();
	}

	@GetMapping("/permissions")
	public List<PermissionResponse> permissions() {
		return accessCatalogService.permissions();
	}

}
