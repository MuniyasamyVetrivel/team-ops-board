package com.teamops.user.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.user.entity.RoleCodes;

/**
 * Rules for editing a role's permissions in the permission matrix:
 * <ul>
 * <li>the Super Admin role is locked: it always holds every permission;</li>
 * <li>Digital Marketing permissions are granted to people, never to a whole role (the module must stay limited to
 * the people who hold them);</li>
 * <li>an editor may only add permissions they hold themselves;</li>
 * <li>a create, edit or manage permission needs the matching view permission.</li>
 * </ul>
 */
final class RolePermissionRules {

	static final String MARKETING_MODULE = "MARKETING";

	/** Each action permission and the view permission it needs (marketing pairs live in MarketingPermissionRules). */
	static final Map<String, String> NEEDS_VIEW = Map.ofEntries(Map.entry("TASK_CREATE", "TASK_VIEW"),
			Map.entry("TASK_EDIT", "TASK_VIEW"), Map.entry("TASK_ASSIGN", "TASK_VIEW"),
			Map.entry("TASK_DELETE", "TASK_VIEW"), Map.entry("TICKET_CREATE", "TICKET_VIEW"),
			Map.entry("TICKET_EDIT", "TICKET_VIEW"), Map.entry("TICKET_ASSIGN", "TICKET_VIEW"),
			Map.entry("SLA_MANAGE", "TICKET_VIEW"), Map.entry("PROJECT_EDIT", "PROJECT_VIEW"),
			Map.entry("APPROVAL_DECIDE", "APPROVAL_VIEW"), Map.entry("APPROVAL_CONFIGURE", "APPROVAL_VIEW"),
			Map.entry("KB_EDIT", "KB_VIEW"), Map.entry("DOCUMENT_EDIT", "DOCUMENT_VIEW"),
			Map.entry("CALENDAR_EDIT", "CALENDAR_VIEW"), Map.entry("REPORT_EXPORT", "REPORT_VIEW"));

	private RolePermissionRules() {
	}

	/**
	 * @param roleCode the role being edited
	 * @param requested the complete new permission set
	 * @param current the role's permissions today
	 * @param moduleByCode every known permission code and its module
	 * @param actor who is editing
	 */
	static void check(String roleCode, Set<String> requested, Set<String> current, Map<String, String> moduleByCode,
			AuthenticatedUser actor) {
		if (RoleCodes.SUPER_ADMIN.equals(roleCode)) {
			throw ApiException.conflict("ROLE_LOCKED", "The Super Admin role always holds every permission");
		}
		Set<String> unknown = new TreeSet<>(requested);
		unknown.removeAll(moduleByCode.keySet());
		if (!unknown.isEmpty()) {
			throw ApiException.badRequest("UNKNOWN_PERMISSION", "Unknown permissions: " + String.join(", ", unknown));
		}
		List<String> marketing = requested.stream()
			.filter(code -> MARKETING_MODULE.equals(moduleByCode.get(code)))
			.sorted()
			.toList();
		if (!marketing.isEmpty()) {
			throw ApiException.badRequest("MARKETING_PERMISSION_ON_ROLE",
					"Grant Digital Marketing permissions to people, not to a role: " + String.join(", ", marketing));
		}
		Set<String> notHeld = new TreeSet<>(requested);
		notHeld.removeAll(current);
		notHeld.removeAll(actor.permissions());
		if (!actor.isSuperAdmin() && !notHeld.isEmpty()) {
			throw ApiException.forbidden("PERMISSION_NOT_HELD",
					"You can only grant permissions you hold: " + String.join(", ", notHeld));
		}
		Set<String> missing = new TreeSet<>();
		NEEDS_VIEW.forEach((action, view) -> {
			if (requested.contains(action) && !requested.contains(view)) {
				missing.add(action + " needs " + view);
			}
		});
		if (!missing.isEmpty()) {
			throw ApiException.badRequest("VIEW_PERMISSION_REQUIRED",
					"Permissions need their view permission: " + String.join(", ", missing));
		}
	}

}
