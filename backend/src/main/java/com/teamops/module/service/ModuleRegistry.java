package com.teamops.module.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.teamops.common.exception.ApiException;

/**
 * Every department tool the application knows, from the {@link ModuleDefinition} beans, sorted by name. Keys and
 * permissions must be unique across modules: a permission belongs to exactly one module, so granting it never opens
 * another tool by accident. Fails at startup otherwise.
 */
@Component
public class ModuleRegistry {

	private final Map<String, ModuleDefinition> byKey = new LinkedHashMap<>();

	@Autowired
	public ModuleRegistry(ObjectProvider<ModuleDefinition> definitions) {
		this(definitions.orderedStream().toList());
	}

	public ModuleRegistry(List<ModuleDefinition> definitions) {
		Map<String, String> permissionOwners = new HashMap<>();
		definitions.stream()
			.sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
			.forEach(definition -> {
				if (byKey.putIfAbsent(definition.key(), definition) != null) {
					throw new IllegalStateException("Two modules use the key " + definition.key());
				}
				for (String permission : List.of(definition.viewPermission(), definition.editPermission())) {
					String owner = permissionOwners.putIfAbsent(permission, definition.key());
					if (owner != null) {
						throw new IllegalStateException(
								"Modules " + owner + " and " + definition.key() + " share the permission " + permission);
					}
				}
			});
	}

	public Collection<ModuleDefinition> all() {
		return byKey.values();
	}

	public Optional<ModuleDefinition> find(String key) {
		return Optional.ofNullable(key == null ? null : byKey.get(key));
	}

	/** The module, or 404 MODULE_NOT_FOUND. */
	public ModuleDefinition require(String key) {
		return find(key).orElseThrow(() -> ApiException.notFound("MODULE_NOT_FOUND", "Unknown module: " + key));
	}

	/**
	 * Rejects a permission set in which a module's edit permission comes without its view permission (a person's
	 * effective permissions: roles and direct grants). 400 VIEW_PERMISSION_REQUIRED.
	 */
	public void assertEditHasView(Set<String> permissions) {
		Set<String> missing = new TreeSet<>();
		byKey.values().forEach(module -> {
			if (permissions.contains(module.editPermission()) && !permissions.contains(module.viewPermission())) {
				missing.add(module.editPermission() + " needs " + module.viewPermission());
			}
		});
		if (!missing.isEmpty()) {
			throw ApiException.badRequest("VIEW_PERMISSION_REQUIRED",
					"Department tool edit permissions need their view permission: " + String.join(", ", missing));
		}
	}

	/** Each module's edit permission and the view permission it needs (for the permission matrix). */
	public Map<String, String> editNeedsView() {
		Map<String, String> pairs = new HashMap<>();
		byKey.values().forEach(module -> pairs.put(module.editPermission(), module.viewPermission()));
		return pairs;
	}

}
