package com.teamops.module.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.repository.DepartmentMemberRepository;
import com.teamops.module.dto.ModuleDtos.Tool;
import com.teamops.module.repository.DepartmentModuleRepository;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Who may use a department tool. A user may open a module when they hold its view permission and the module is
 * switched on for one of their departments (their primary department or any department they are a member of); a
 * change also needs its edit permission. The Super Admin may use every module that is switched on for at least one
 * active department, so switching a module off everywhere turns it off for everyone. Modules of inactive departments
 * do not count.
 * <p>
 * {@code ModuleAccessInterceptor} applies {@link #requireAccess} to every request under {@code /api/modules/{key}/}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModuleAccessService {

	private final ModuleRegistry registry;

	private final DepartmentModuleRepository departmentModules;

	private final DepartmentMemberRepository departmentMembers;

	private final UserRepository users;

	/** The modules in the user's sidebar: switched on for them and permitted to them. */
	public List<Tool> toolsFor(AuthenticatedUser user) {
		List<ModuleDefinition> permitted = registry.all()
			.stream()
			.filter(module -> user.hasPermission(module.viewPermission()))
			.toList();
		if (permitted.isEmpty()) {
			return List.of();
		}
		Set<String> enabled = enabledKeysFor(user);
		return permitted.stream()
			.filter(module -> enabled.contains(module.key()))
			.map(module -> Tool.of(module, user.hasPermission(module.editPermission())))
			.toList();
	}

	/**
	 * Passes when the user may use the module: 404 MODULE_NOT_FOUND for an unknown key, 403 FORBIDDEN without the
	 * view permission (and, to change anything, the edit permission), 403 MODULE_DISABLED when the module is not
	 * switched on for any of the user's departments.
	 */
	public ModuleDefinition requireAccess(AuthenticatedUser user, String key, boolean edit) {
		ModuleDefinition module = registry.require(key);
		boolean permitted = user.hasPermission(module.viewPermission())
				&& (!edit || user.hasPermission(module.editPermission()));
		if (!permitted) {
			throw ApiException.forbidden("FORBIDDEN", "You do not have permission to perform this action");
		}
		if (!enabledKeysFor(user).contains(key)) {
			throw ApiException.forbidden("MODULE_DISABLED",
					module.name() + " is not switched on for your department");
		}
		return module;
	}

	/**
	 * Who reminders and notifications of a module go to: active users who may open it (view permission and the
	 * module switched on for one of their departments), and active Super Admins while it is switched on anywhere.
	 */
	public List<User> recipients(String key) {
		ModuleDefinition module = registry.require(key);
		Set<Long> departments = departmentModules.findEnabledDepartmentIds(key);
		if (departments.isEmpty()) {
			return List.of();
		}
		Map<Long, User> result = new LinkedHashMap<>();
		users.findByRoleAndStatus(RoleCodes.SUPER_ADMIN, UserStatus.ACTIVE).forEach(user -> result.put(user.getId(), user));
		List<User> holders = new ArrayList<>(users.findActiveWithPermission(module.viewPermission(), UserStatus.ACTIVE));
		holders.removeIf(user -> result.containsKey(user.getId()));
		if (!holders.isEmpty()) {
			Set<Long> members = departmentMembers.findMembersAmong(holders.stream().map(User::getId).toList(),
					departments);
			holders.stream()
				.filter(user -> departments.contains(user.getDepartment().getId()) || members.contains(user.getId()))
				.forEach(user -> result.put(user.getId(), user));
		}
		return List.copyOf(result.values());
	}

	private Set<String> enabledKeysFor(AuthenticatedUser user) {
		return user.isSuperAdmin() ? departmentModules.findEnabledKeys()
				: departmentModules.findEnabledKeysFor(user.id(), user.departmentId());
	}

}
