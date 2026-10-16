package com.teamops.module.service;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A department tool (Bench Management, Vendors, Finance, ...). Each module registers one as a Spring bean; the
 * {@link ModuleRegistry} collects them. Which departments a module is switched on for is data
 * ({@code department_modules}, edited by MODULE_MANAGE holders); everything else about it is code.
 *
 * @param key stable identifier, lower case with dashes ("bench"); its API lives under {@code /api/modules/{key}/}
 * @param name shown in the sidebar and in Admin settings
 * @param icon Lucide icon name the frontend draws ("Users", "Building")
 * @param description one line for Admin settings
 * @param viewPermission needed to open the module (GET requests); granted to SUPER_ADMIN only by default
 * @param editPermission needed to change anything in it (every other method); needs the view permission as well
 */
public record ModuleDefinition(String key, String name, String icon, String description, String viewPermission,
		String editPermission) {

	public static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]{1,39}");

	private static final Pattern PERMISSION = Pattern.compile("[A-Z][A-Z0-9_]{1,59}");

	public ModuleDefinition {
		Objects.requireNonNull(key, "key");
		if (!KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("Module key must be lower case letters, digits and dashes: " + key);
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Module " + key + " needs a name");
		}
		if (icon == null || icon.isBlank()) {
			throw new IllegalArgumentException("Module " + key + " needs an icon");
		}
		requirePermission(key, viewPermission);
		requirePermission(key, editPermission);
		if (viewPermission.equals(editPermission)) {
			throw new IllegalArgumentException("Module " + key + " needs separate view and edit permissions");
		}
	}

	private static void requirePermission(String key, String permission) {
		if (permission == null || !PERMISSION.matcher(permission).matches()) {
			throw new IllegalArgumentException("Module " + key + " has an invalid permission code: " + permission);
		}
	}

}
