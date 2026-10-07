package com.teamops.user.dto;

import com.teamops.user.entity.Permission;

public record PermissionResponse(String code, String name, String module, String description) {

	public static PermissionResponse of(Permission permission) {
		return new PermissionResponse(permission.getCode(), permission.getName(), permission.getModule(),
				permission.getDescription());
	}

}
