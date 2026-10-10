package com.teamops.user.controller;

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
import com.teamops.user.dto.PermissionResponse;
import com.teamops.user.dto.RoleResponse;
import com.teamops.user.dto.UpdateRolePermissionsRequest;
import com.teamops.user.service.AccessCatalogService;
import com.teamops.user.service.RolePermissionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Role and permission catalogue, and the permission matrix (each role's permission set). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('USER_MANAGE', 'PERMISSION_MANAGE')")
public class AccessCatalogController {

	private final AccessCatalogService accessCatalogService;

	private final RolePermissionService rolePermissionService;

	@GetMapping("/roles")
	public List<RoleResponse> roles() {
		return accessCatalogService.roles();
	}

	@GetMapping("/permissions")
	public List<PermissionResponse> permissions() {
		return accessCatalogService.permissions();
	}

	/** Role-wide changes affect everyone holding the role, so only a Super Admin makes them. */
	@PutMapping("/roles/{code}/permissions")
	@PreAuthorize("hasAuthority('PERMISSION_MANAGE') and hasRole('SUPER_ADMIN')")
	public RoleResponse updatePermissions(@PathVariable String code,
			@Valid @RequestBody UpdateRolePermissionsRequest request, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return rolePermissionService.update(code, request, actor, ClientInfo.from(http));
	}

}
