package com.teamops.module.dto;

import java.util.List;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.module.service.ModuleDefinition;

import jakarta.validation.constraints.NotNull;

public final class ModuleDtos {

	private ModuleDtos() {
	}

	/** A department tool in the current user's sidebar ("Department tools"). */
	public record Tool(String key, String name, String icon, String description, boolean canEdit) {

		public static Tool of(ModuleDefinition module, boolean canEdit) {
			return new Tool(module.key(), module.name(), module.icon(), module.description(), canEdit);
		}

	}

	/** A module in Admin settings with the departments it is switched on for (by name). */
	public record ModuleSettings(String key, String name, String icon, String description, String viewPermission,
			String editPermission, List<DepartmentSummary> departments) {

		public static ModuleSettings of(ModuleDefinition module, List<DepartmentSummary> departments) {
			return new ModuleSettings(module.key(), module.name(), module.icon(), module.description(),
					module.viewPermission(), module.editPermission(), departments);
		}

	}

	/** Switch a module on or off for one department. */
	public record UpdateSwitch(@NotNull Boolean enabled) {
	}

}
