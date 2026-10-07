package com.teamops.common.security;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.department.entity.DepartmentMemberRole;
import com.teamops.department.repository.DepartmentMemberRepository;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.entity.PermissionCodes;
import com.teamops.user.entity.RoleCodes;

import lombok.RequiredArgsConstructor;

/**
 * Turns the current user into an {@link AccessScope}. A DEPARTMENT_MANAGER manages their primary department, every
 * department where they are {@code departments.manager_id}, and every department where they hold a MANAGER
 * membership.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessScopeService {

	private final DepartmentRepository departmentRepository;

	private final DepartmentMemberRepository departmentMemberRepository;

	public AccessScope scopeFor(AuthenticatedUser user) {
		if (user.isSuperAdmin()) {
			return AccessScope.all(user.id());
		}
		if (user.hasRole(RoleCodes.DEPARTMENT_MANAGER)) {
			return AccessScope.departments(user.id(), managedDepartmentIds(user));
		}
		return AccessScope.own(user.id());
	}

	public Set<Long> managedDepartmentIds(AuthenticatedUser user) {
		Set<Long> ids = new HashSet<>(departmentRepository.findIdsManagedBy(user.id()));
		ids.addAll(departmentMemberRepository.findDepartmentIds(user.id(), DepartmentMemberRole.MANAGER));
		ids.add(user.departmentId());
		return ids;
	}

	/** Holders of DEPARTMENT_MANAGE manage every department; department managers manage their own. */
	public boolean canManageDepartment(AuthenticatedUser user, Long departmentId) {
		return user.hasPermission(PermissionCodes.DEPARTMENT_MANAGE) || scopeFor(user).coversDepartment(departmentId);
	}

	/** Whether the viewer may see another user's work (tasks, workload, tickets, activity). */
	public boolean canViewWorkOf(AuthenticatedUser viewer, Long targetUserId, Long targetDepartmentId) {
		return scopeFor(viewer).coversUser(targetUserId, targetDepartmentId);
	}

}
