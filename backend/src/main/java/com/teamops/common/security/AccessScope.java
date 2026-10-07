package com.teamops.common.security;

import java.util.Set;

/**
 * What data a user may see or manage:
 * <ul>
 * <li>{@link Kind#ALL}: Super Admin, every department.</li>
 * <li>{@link Kind#DEPARTMENTS}: a department manager, the departments they manage, plus their own records.</li>
 * <li>{@link Kind#OWN}: everyone else, only their own records.</li>
 * </ul>
 * Services translate this into query filters; it is never derived from client input.
 */
public record AccessScope(Kind kind, Long userId, Set<Long> departmentIds) {

	public enum Kind {

		ALL, DEPARTMENTS, OWN

	}

	public AccessScope {
		departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
	}

	public static AccessScope all(Long userId) {
		return new AccessScope(Kind.ALL, userId, Set.of());
	}

	public static AccessScope departments(Long userId, Set<Long> departmentIds) {
		return new AccessScope(Kind.DEPARTMENTS, userId, departmentIds);
	}

	public static AccessScope own(Long userId) {
		return new AccessScope(Kind.OWN, userId, Set.of());
	}

	public boolean isAll() {
		return kind == Kind.ALL;
	}

	public boolean coversDepartment(Long departmentId) {
		return switch (kind) {
			case ALL -> true;
			case DEPARTMENTS -> departmentIds.contains(departmentId);
			case OWN -> false;
		};
	}

	/** Whether records owned by the given user (in the given primary department) are within scope. */
	public boolean coversUser(Long targetUserId, Long targetDepartmentId) {
		return switch (kind) {
			case ALL -> true;
			case DEPARTMENTS -> userId.equals(targetUserId) || departmentIds.contains(targetDepartmentId);
			case OWN -> userId.equals(targetUserId);
		};
	}

}
