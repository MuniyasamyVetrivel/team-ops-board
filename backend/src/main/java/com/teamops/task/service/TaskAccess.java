package com.teamops.task.service;

import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.task.dto.TaskPermissions;
import com.teamops.task.entity.Task;
import com.teamops.user.entity.User;

/**
 * Task authorization for one actor (brief section 4: backend-enforced, department-aware).
 * <ul>
 * <li><b>View</b>: Super Admin; tasks in a department the actor manages; or tasks they are assigned to, created or
 * watch.</li>
 * <li><b>Edit</b> (TASK_EDIT): Super Admin, managers of the task's department, or the assignee/creator. Watchers
 * can view and comment only.</li>
 * <li><b>Assign others</b> (TASK_ASSIGN): only to people within the actor's scope. Anyone who can edit may take a
 * task themselves.</li>
 * <li><b>Cancel</b> (TASK_DELETE): Super Admin or a manager of the task's department, or its assignee/creator.</li>
 * </ul>
 */
public record TaskAccess(AuthenticatedUser actor, AccessScope scope) {

	static final String TASK_CREATE = "TASK_CREATE";

	static final String TASK_EDIT = "TASK_EDIT";

	static final String TASK_ASSIGN = "TASK_ASSIGN";

	static final String TASK_DELETE = "TASK_DELETE";

	public Long me() {
		return actor.id();
	}

	public boolean canView(Task task) {
		return manages(task) || task.isWatchedBy(me());
	}

	public boolean canEdit(Task task) {
		return actor.hasPermission(TASK_EDIT) && manages(task);
	}

	public boolean canCancel(Task task) {
		return actor.hasPermission(TASK_DELETE) && manages(task);
	}

	/** {@code target == null} means unassign. */
	public boolean canAssign(Task task, User target) {
		if (!canEdit(task)) {
			return false;
		}
		if (target == null) {
			return actor.hasPermission(TASK_ASSIGN) || task.isAssignedTo(me());
		}
		return target.getId().equals(me()) || canAssignOthersTo(target);
	}

	/** Create in a department: Super Admin anywhere, managers in their departments, others in their own. */
	public boolean canCreateIn(Long departmentId) {
		return actor.hasPermission(TASK_CREATE) && (scope.coversDepartment(departmentId)
				|| departmentId.equals(actor.departmentId()));
	}

	/** Assignee chosen at creation time; {@code null} or self is always fine. */
	public boolean canAssignOnCreate(User target) {
		return target == null || target.getId().equals(me()) || canAssignOthersTo(target);
	}

	public TaskPermissions permissions(Task task) {
		boolean canEdit = canEdit(task);
		return new TaskPermissions(canEdit, canEdit && actor.hasPermission(TASK_ASSIGN), canCancel(task),
				canView(task), canView(task));
	}

	private boolean canAssignOthersTo(User target) {
		return actor.hasPermission(TASK_ASSIGN) && scope.coversUser(target.getId(), target.getDepartment().getId());
	}

	/** Super Admin, managed department, or personally involved as assignee/creator. */
	private boolean manages(Task task) {
		return scope.coversDepartment(task.getDepartment().getId()) || task.isAssignedTo(me())
				|| task.isCreatedBy(me());
	}

}
