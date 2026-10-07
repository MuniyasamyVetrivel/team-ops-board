package com.teamops.task.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.entity.Department;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.task.entity.Task;
import com.teamops.user.entity.User;

class TaskAccessTest {

	private final Department webDev = TestFixtures.department(5L, "WEBDEV", "Web Development");

	private final Department marketing = TestFixtures.department(7L, "DM", "Digital Marketing");

	private final User karthik = member(4L, webDev);

	private final User arun = member(21L, marketing);

	/** Web Development manager (manages department 5 only). */
	private final TaskAccess manager = new TaskAccess(new AuthenticatedUser(30L, "sanjay@teamops.local", "Sanjay",
			5L, Set.of("DEPARTMENT_MANAGER"),
			Set.of("TASK_VIEW", "TASK_CREATE", "TASK_EDIT", "TASK_ASSIGN", "TASK_DELETE")),
			AccessScope.departments(30L, Set.of(5L)));

	/** Karthik, an employee (no TASK_ASSIGN, no TASK_DELETE). */
	private final TaskAccess employee = new TaskAccess(SliceAuth.EMPLOYEE, AccessScope.own(SliceAuth.EMPLOYEE.id()));

	private final TaskAccess admin = new TaskAccess(SliceAuth.SUPER_ADMIN, AccessScope.all(1L));

	@Test
	void superAdminCanDoEverything() {
		Task task = task(marketing, arun, null);

		assertThat(admin.canView(task)).isTrue();
		assertThat(admin.canEdit(task)).isTrue();
		assertThat(admin.canCancel(task)).isTrue();
		assertThat(admin.canAssign(task, karthik)).isTrue();
		assertThat(admin.canCreateIn(99L)).isTrue();
	}

	@Test
	void managerWorksWithinTheirDepartmentsOnly() {
		Task ownDepartment = task(webDev, null, null);
		Task otherDepartment = task(marketing, arun, null);

		assertThat(manager.canEdit(ownDepartment)).isTrue();
		assertThat(manager.canAssign(ownDepartment, karthik)).isTrue();
		assertThat(manager.canAssign(ownDepartment, arun)).as("arun is outside the manager's scope").isFalse();
		assertThat(manager.canView(otherDepartment)).isFalse();
		assertThat(manager.canCreateIn(7L)).isFalse();
		assertThat(manager.canCreateIn(5L)).isTrue();
	}

	@Test
	void employeeWorksOnTheirOwnTasks() {
		Task mine = task(webDev, karthik, null);
		Task colleagues = task(webDev, member(41L, webDev), null);

		assertThat(employee.canView(mine)).isTrue();
		assertThat(employee.canEdit(mine)).isTrue();
		assertThat(employee.canCancel(mine)).as("employees lack TASK_DELETE").isFalse();
		assertThat(employee.canView(colleagues)).as("same department is not enough for employees").isFalse();
		assertThat(employee.canCreateIn(5L)).as("own primary department").isTrue();
		assertThat(employee.canCreateIn(7L)).isFalse();
	}

	@Test
	void employeeCanTakeOrDropTheirOwnTaskButNotAssignOthers() {
		Task created = task(webDev, null, karthik);
		Task mine = task(webDev, karthik, null);

		assertThat(employee.canAssign(created, karthik)).as("take it").isTrue();
		assertThat(employee.canAssign(created, member(41L, webDev))).as("needs TASK_ASSIGN").isFalse();
		assertThat(employee.canAssign(mine, null)).as("unassign self").isTrue();
		assertThat(employee.canAssignOnCreate(karthik)).isTrue();
		assertThat(employee.canAssignOnCreate(member(41L, webDev))).isFalse();
	}

	@Test
	void watchersCanViewButNotEdit() {
		Task watched = task(marketing, arun, null);
		watched.getWatchers().add(karthik);

		assertThat(employee.canView(watched)).isTrue();
		assertThat(employee.canEdit(watched)).isFalse();
		assertThat(employee.permissions(watched).canComment()).isTrue();
		assertThat(employee.permissions(watched).canEdit()).isFalse();
	}

	private static User member(long id, Department department) {
		User user = TestFixtures.user(id, "user" + id + "@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
		user.setDepartment(department);
		return user;
	}

	private static Task task(Department department, User assignee, User createdBy) {
		Task task = new Task();
		task.setDepartment(department);
		task.setAssignee(assignee);
		task.setCreatedBy(createdBy);
		return task;
	}

}
