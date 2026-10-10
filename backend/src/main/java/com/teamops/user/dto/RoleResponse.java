package com.teamops.user.dto;

import java.util.List;

import com.teamops.user.entity.Permission;
import com.teamops.user.entity.Role;

public record RoleResponse(Long id, String code, String name, String description, List<String> permissions,
		Integer version) {

	public static RoleResponse of(Role role) {
		return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
				role.getPermissions().stream().map(Permission::getCode).sorted().toList(), role.getVersion());
	}

}
