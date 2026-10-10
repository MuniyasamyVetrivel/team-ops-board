package com.teamops.user.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.user.dto.RoleResponse;
import com.teamops.user.dto.UpdateRolePermissionsRequest;
import com.teamops.user.entity.Permission;
import com.teamops.user.entity.Role;
import com.teamops.user.repository.PermissionRepository;
import com.teamops.user.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * The permission matrix: replaces the permission set of a role (see {@link RolePermissionRules}). Authorities are
 * resolved from the database on every request, so a change applies to everyone holding the role straight away.
 */
@Service
@RequiredArgsConstructor
public class RolePermissionService {

	private final RoleRepository roleRepository;

	private final PermissionRepository permissionRepository;

	private final AuditService auditService;

	@Transactional
	public RoleResponse update(String roleCode, UpdateRolePermissionsRequest request, AuthenticatedUser actor,
			ClientInfo client) {
		Role role = roleRepository.findByCode(roleCode)
			.orElseThrow(() -> ApiException.notFound("ROLE_NOT_FOUND", "Role not found"));
		List<Permission> catalogue = permissionRepository.findAll();
		Map<String, String> moduleByCode = catalogue.stream()
			.collect(Collectors.toMap(Permission::getCode, Permission::getModule));
		Set<String> current = codes(role.getPermissions());
		Set<String> requested = new TreeSet<>(request.permissions());
		RolePermissionRules.check(role.getCode(), requested, current, moduleByCode, actor);
		if (!Objects.equals(role.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this role just now. Reload and try again.");
		}

		Set<String> added = new TreeSet<>(requested);
		added.removeAll(current);
		Set<String> removed = new TreeSet<>(current);
		removed.removeAll(requested);
		if (added.isEmpty() && removed.isEmpty()) {
			return RoleResponse.of(role);
		}
		role.getPermissions().removeIf(p -> removed.contains(p.getCode()));
		catalogue.stream().filter(p -> added.contains(p.getCode())).forEach(role.getPermissions()::add);
		roleRepository.flush();

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("role", role.getCode());
		details.put("added", List.copyOf(added));
		details.put("removed", List.copyOf(removed));
		auditService.record(AuditAction.ROLE_PERMISSIONS_CHANGED, actor.id(), "ROLE", role.getId(), details, client);
		return RoleResponse.of(role);
	}

	private static Set<String> codes(Set<Permission> permissions) {
		return permissions.stream().map(Permission::getCode).collect(Collectors.toCollection(TreeSet::new));
	}

}
