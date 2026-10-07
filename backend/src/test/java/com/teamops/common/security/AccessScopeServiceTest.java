package com.teamops.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.department.entity.DepartmentMemberRole;
import com.teamops.department.repository.DepartmentMemberRepository;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.SliceAuth;

class AccessScopeServiceTest {

	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

	private final DepartmentMemberRepository memberRepository = mock(DepartmentMemberRepository.class);

	private final AccessScopeService service = new AccessScopeService(departmentRepository, memberRepository);

	/** Manager whose primary department is 5, manager_id of department 6, MANAGER member of department 8. */
	private final AuthenticatedUser manager = new AuthenticatedUser(30L, "lead@teamops.local", "Lead", 5L,
			Set.of("DEPARTMENT_MANAGER"), Set.of("TEAM_VIEW"));

	@Test
	void superAdminSeesEverything() {
		AccessScope scope = service.scopeFor(SliceAuth.SUPER_ADMIN);

		assertThat(scope.isAll()).isTrue();
		assertThat(scope.coversDepartment(999L)).isTrue();
		assertThat(scope.coversUser(123L, 999L)).isTrue();
		verifyNoInteractions(departmentRepository, memberRepository);
	}

	@Test
	void managerScopeCombinesPrimaryManagedAndManagerMemberships() {
		when(departmentRepository.findIdsManagedBy(30L)).thenReturn(Set.of(6L));
		when(memberRepository.findDepartmentIds(30L, DepartmentMemberRole.MANAGER)).thenReturn(Set.of(8L));

		AccessScope scope = service.scopeFor(manager);

		assertThat(scope.kind()).isEqualTo(AccessScope.Kind.DEPARTMENTS);
		assertThat(scope.departmentIds()).containsExactlyInAnyOrder(5L, 6L, 8L);
		assertThat(scope.coversUser(77L, 6L)).isTrue();
		assertThat(scope.coversUser(77L, 1L)).isFalse();
		assertThat(scope.coversUser(30L, 1L)).as("own records are always visible").isTrue();
	}

	@Test
	void employeeSeesOnlyTheirOwnRecords() {
		AccessScope scope = service.scopeFor(SliceAuth.EMPLOYEE);

		assertThat(scope.kind()).isEqualTo(AccessScope.Kind.OWN);
		assertThat(scope.coversDepartment(SliceAuth.EMPLOYEE.departmentId())).isFalse();
		assertThat(scope.coversUser(SliceAuth.EMPLOYEE.id(), 5L)).isTrue();
		assertThat(scope.coversUser(99L, 5L)).isFalse();
	}

	@Test
	void departmentManagePermissionManagesEveryDepartment() {
		AuthenticatedUser admin = new AuthenticatedUser(60L, "ops@teamops.local", "Ops", 1L, Set.of("EMPLOYEE"),
				Set.of("DEPARTMENT_MANAGE"));

		assertThat(service.canManageDepartment(admin, 42L)).isTrue();
		assertThat(service.canManageDepartment(SliceAuth.EMPLOYEE, 5L)).isFalse();
	}

	@Test
	void managerCanViewWorkOfTheirDepartmentOnly() {
		when(departmentRepository.findIdsManagedBy(30L)).thenReturn(Set.of());
		when(memberRepository.findDepartmentIds(30L, DepartmentMemberRole.MANAGER)).thenReturn(Set.of());

		assertThat(service.canViewWorkOf(manager, 77L, 5L)).isTrue();
		assertThat(service.canViewWorkOf(manager, 77L, 6L)).isFalse();
	}

}
