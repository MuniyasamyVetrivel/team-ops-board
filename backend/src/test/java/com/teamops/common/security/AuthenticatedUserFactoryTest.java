package com.teamops.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;
import com.teamops.user.repository.PermissionRepository;

class AuthenticatedUserFactoryTest {

	private final PermissionRepository permissionRepository = mock(PermissionRepository.class);

	private final AuthenticatedUserFactory factory = new AuthenticatedUserFactory(permissionRepository);

	@Test
	void superAdminGetsEveryPermissionInTheCatalogue() {
		when(permissionRepository.findAllCodes()).thenReturn(List.of("TASK_VIEW", "AUDIT_VIEW", "SEO_EDIT"));
		User admin = TestFixtures.user(1L, "rakesh@teamops.local", TestFixtures.role(1L, "SUPER_ADMIN"));

		AuthenticatedUser principal = factory.from(admin);

		assertThat(principal.isSuperAdmin()).isTrue();
		assertThat(principal.permissions()).containsExactlyInAnyOrder("TASK_VIEW", "AUDIT_VIEW", "SEO_EDIT");
	}

	@Test
	void employeeGetsRolePermissionsPlusDirectGrants() {
		User employee = TestFixtures.user(5L, "arun.kumar@teamops.local",
				TestFixtures.role(3L, "EMPLOYEE", "TASK_VIEW", "TICKET_CREATE"), "MARKETING_VIEW", "SEO_EDIT");

		AuthenticatedUser principal = factory.from(employee);

		assertThat(principal.isSuperAdmin()).isFalse();
		assertThat(principal.roles()).containsExactly("EMPLOYEE");
		assertThat(principal.permissions()).containsExactlyInAnyOrder("TASK_VIEW", "TICKET_CREATE", "MARKETING_VIEW",
				"SEO_EDIT");
		assertThat(principal.departmentId()).isEqualTo(7L);
		verifyNoInteractions(permissionRepository);
	}

	@Test
	void authoritiesExposeRolesWithPrefixAndPermissionsAsIs() {
		User employee = TestFixtures.user(5L, "a@teamops.local", TestFixtures.role(3L, "EMPLOYEE", "TASK_VIEW"));

		List<String> authorities = factory.from(employee)
			.authorities()
			.stream()
			.map(Object::toString)
			.toList();

		assertThat(authorities).containsExactlyInAnyOrder("ROLE_EMPLOYEE", "TASK_VIEW");
	}

}
