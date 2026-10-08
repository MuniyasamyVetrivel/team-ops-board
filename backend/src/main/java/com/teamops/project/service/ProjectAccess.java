package com.teamops.project.service;

import java.util.HashSet;
import java.util.Set;

import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.project.entity.Project;

/**
 * Project authorization for one actor.
 * <ul>
 * <li><b>View</b> (PROJECT_VIEW): Super Admin; projects of the actor's own or managed departments; projects they own
 * or are a member of.</li>
 * <li><b>Edit</b> (PROJECT_EDIT): Super Admin, managers of the project's department, or the owner.</li>
 * <li><b>Create</b> (PROJECT_EDIT): in a department the actor manages (Super Admin: anywhere).</li>
 * </ul>
 */
public record ProjectAccess(AuthenticatedUser actor, AccessScope scope) {

	static final String PROJECT_EDIT = "PROJECT_EDIT";

	public Long me() {
		return actor.id();
	}

	/** Own department plus managed departments (ignored for ALL). */
	public Set<Long> departmentIds() {
		Set<Long> ids = new HashSet<>(scope.departmentIds());
		ids.add(actor.departmentId());
		return ids;
	}

	public boolean canView(Project project) {
		Long departmentId = project.getDepartment().getId();
		return scope.coversDepartment(departmentId) || departmentId.equals(actor.departmentId())
				|| project.isOwnedBy(me()) || project.hasMember(me());
	}

	public boolean canEdit(Project project) {
		return actor.hasPermission(PROJECT_EDIT)
				&& (scope.coversDepartment(project.getDepartment().getId()) || project.isOwnedBy(me()));
	}

	public boolean canCreateIn(Long departmentId) {
		return actor.hasPermission(PROJECT_EDIT) && scope.coversDepartment(departmentId);
	}

}
