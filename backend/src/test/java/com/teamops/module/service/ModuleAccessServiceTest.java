package com.teamops.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentMemberRepository;
import com.teamops.module.dto.ModuleDtos.Tool;
import com.teamops.module.repository.DepartmentModuleRepository;
import com.teamops.support.SliceAuth;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

class ModuleAccessServiceTest {

	private final DepartmentModuleRepository departmentModules = mock(DepartmentModuleRepository.class);

	private final DepartmentMemberRepository departmentMembers = mock(DepartmentMemberRepository.class);

	private final UserRepository users = mock(UserRepository.class);

	private final ModuleAccessService service = new ModuleAccessService(
			new ModuleRegistry(List.of(ModuleRegistryTest.BENCH, ModuleRegistryTest.VENDORS)), departmentModules,
			departmentMembers, users);

	/** HR employee (department 3) who may view and edit the bench. */
	private final AuthenticatedUser recruiter = new AuthenticatedUser(20L, "hr@teamops.local", "Recruiter", 3L,
			Set.of(RoleCodes.EMPLOYEE), Set.of("TASK_VIEW", "BENCH_VIEW", "BENCH_EDIT"));

	/** Pre-sales employee (department 4) with read-only bench access. */
	private final AuthenticatedUser presales = new AuthenticatedUser(21L, "ps@teamops.local", "Pre-Sales", 4L,
			Set.of(RoleCodes.EMPLOYEE), Set.of("TASK_VIEW", "BENCH_VIEW"));

	@Test
	void theSidebarListsModulesSwitchedOnAndPermitted() {
		when(departmentModules.findEnabledKeysFor(20L, 3L)).thenReturn(Set.of("bench", "vendors"));
		when(departmentModules.findEnabledKeysFor(21L, 4L)).thenReturn(Set.of("vendors"));

		assertThat(service.toolsFor(recruiter)).containsExactly(Tool.of(ModuleRegistryTest.BENCH, true));
		assertThat(service.toolsFor(presales)).as("bench is not switched on for Pre-Sales").isEmpty();
	}

	@Test
	void peopleWithoutModulePermissionsCostNoQuery() {
		assertThat(service.toolsFor(SliceAuth.EMPLOYEE)).isEmpty();
		verifyNoInteractions(departmentModules);
	}

	@Test
	void theSuperAdminSeesModulesSwitchedOnAnywhere() {
		when(departmentModules.findEnabledKeys()).thenReturn(Set.of("vendors"));
		AuthenticatedUser superAdmin = new AuthenticatedUser(1L, "r@teamops.local", "Rakesh", 1L,
				Set.of(RoleCodes.SUPER_ADMIN), Set.of("BENCH_VIEW", "BENCH_EDIT", "VENDOR_VIEW", "VENDOR_EDIT"));

		assertThat(service.toolsFor(superAdmin)).containsExactly(Tool.of(ModuleRegistryTest.VENDORS, true));
		assertThatCode(() -> service.requireAccess(superAdmin, "vendors", true)).doesNotThrowAnyException();
		assertCode(() -> service.requireAccess(superAdmin, "bench", false), "MODULE_DISABLED");
	}

	@Test
	void requireAccessChecksTheKeyThePermissionAndTheSwitch() {
		when(departmentModules.findEnabledKeysFor(20L, 3L)).thenReturn(Set.of("bench"));
		when(departmentModules.findEnabledKeysFor(21L, 4L)).thenReturn(Set.of("bench"));

		assertThat(service.requireAccess(recruiter, "bench", true)).isEqualTo(ModuleRegistryTest.BENCH);
		assertThatCode(() -> service.requireAccess(presales, "bench", false)).doesNotThrowAnyException();
		assertCode(() -> service.requireAccess(presales, "bench", true), "FORBIDDEN");
		assertCode(() -> service.requireAccess(recruiter, "vendors", false), "FORBIDDEN");
		assertCode(() -> service.requireAccess(recruiter, "finance", false), "MODULE_NOT_FOUND");

		// An edit permission without the view permission opens nothing.
		AuthenticatedUser editOnly = new AuthenticatedUser(22L, "e@teamops.local", "Edit only", 3L,
				Set.of(RoleCodes.EMPLOYEE), Set.of("BENCH_EDIT"));
		assertCode(() -> service.requireAccess(editOnly, "bench", true), "FORBIDDEN");
	}

	@Test
	void aPermittedUserOutsideTheModulesDepartmentsIsTurnedAway() {
		when(departmentModules.findEnabledKeysFor(21L, 4L)).thenReturn(Set.of());

		assertCode(() -> service.requireAccess(presales, "bench", false), "MODULE_DISABLED");
	}

	@Test
	void remindersGoToPermittedUsersInTheModulesDepartmentsAndToSuperAdmins() {
		when(departmentModules.findEnabledDepartmentIds("bench")).thenReturn(Set.of(3L, 7L));
		User superAdmin = user(1L, 1L);
		User inPrimary = user(20L, 3L);
		User asMember = user(21L, 4L);
		User outside = user(22L, 4L);
		when(users.findByRoleAndStatus(RoleCodes.SUPER_ADMIN, UserStatus.ACTIVE)).thenReturn(List.of(superAdmin));
		when(users.findActiveWithPermission("BENCH_VIEW", UserStatus.ACTIVE))
			.thenReturn(List.of(superAdmin, inPrimary, asMember, outside));
		when(departmentMembers.findMembersAmong(any(), any())).thenReturn(Set.of(21L));

		assertThat(service.recipients("bench")).containsExactly(superAdmin, inPrimary, asMember);
	}

	@Test
	void aModuleSwitchedOffEverywhereRemindsNobody() {
		when(departmentModules.findEnabledDepartmentIds("bench")).thenReturn(Set.of());

		assertThat(service.recipients("bench")).isEmpty();
		verifyNoInteractions(users);
	}

	private static User user(Long id, Long departmentId) {
		Department department = mock(Department.class);
		when(department.getId()).thenReturn(departmentId);
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getDepartment()).thenReturn(department);
		return user;
	}

	private static void assertCode(ThrowingCallable call, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo(code));
	}

}
