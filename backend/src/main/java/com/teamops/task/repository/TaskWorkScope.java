package com.teamops.task.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.teamops.common.security.AccessScope;
import com.teamops.task.entity.TaskStatus;

/**
 * The SQL form of {@link TaskSpecifications#workOf}: the work a viewer counts on dashboards and reports. Everything
 * (ALL), tasks in managed departments plus the viewer's own assignments (DEPARTMENTS), or only their own assignments
 * (OWN). Conditions use the alias {@code t} for {@code tasks}.
 */
public final class TaskWorkScope {

	/** The active statuses, bound as {@code :active}. */
	public static final List<String> ACTIVE = TaskStatus.ACTIVE.stream().map(Enum::name).toList();

	private TaskWorkScope() {
	}

	/** Parameters for {@link #clause}: {@code :me}, {@code :scopeDepartments} and {@code :active}. */
	public static MapSqlParameterSource params(AccessScope scope) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("active", ACTIVE)
			.addValue("me", scope.userId());
		if (scope.kind() == AccessScope.Kind.DEPARTMENTS && !scope.departmentIds().isEmpty()) {
			params.addValue("scopeDepartments", scope.departmentIds());
		}
		return params;
	}

	public static String clause(AccessScope scope) {
		return switch (scope.kind()) {
			case ALL -> "1 = 1";
			case DEPARTMENTS -> scope.departmentIds().isEmpty() ? "t.assignee_id = :me"
					: "(t.department_id in (:scopeDepartments) or t.assignee_id = :me)";
			case OWN -> "t.assignee_id = :me";
		};
	}

}
