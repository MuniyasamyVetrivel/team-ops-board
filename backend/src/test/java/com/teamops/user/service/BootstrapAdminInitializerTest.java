package com.teamops.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.TestFixtures;
import com.teamops.user.dto.CreateUserRequest;
import com.teamops.user.repository.UserRepository;

class BootstrapAdminInitializerTest {

	private final UserRepository userRepository = mock(UserRepository.class);

	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

	private final UserService userService = mock(UserService.class);

	private BootstrapAdminInitializer initializer(String email, String password) {
		return new BootstrapAdminInitializer(new BootstrapAdminProperties(email, password, "Ada", "Admin", "HR", "E-0001"),
				userRepository, departmentRepository, userService);
	}

	@Test
	void createsTheFirstSuperAdminOnAnEmptyDatabase() {
		when(departmentRepository.findByCode("HR")).thenReturn(Optional.of(TestFixtures.department(3L, "HR", "HR")));

		initializer("admin@example.com", "Bootstrap-Pass-2026").run(null);

		ArgumentCaptor<CreateUserRequest> request = ArgumentCaptor.forClass(CreateUserRequest.class);
		verify(userService).create(request.capture(), isNull(), any());
		assertThat(request.getValue().email()).isEqualTo("admin@example.com");
		assertThat(request.getValue().lastName()).isEqualTo("Admin");
		assertThat(request.getValue().departmentId()).isEqualTo(3L);
		assertThat(request.getValue().roles()).isEqualTo(Set.of("SUPER_ADMIN"));
	}

	@Test
	void doesNothingOnceAnyUserExistsOrWhenNotConfigured() {
		when(userRepository.count()).thenReturn(1L);
		initializer("admin@example.com", "Bootstrap-Pass-2026").run(null);
		initializer("", "").run(null);
		verifyNoInteractions(userService);
	}

	@Test
	void rejectsAShortPassword() {
		assertThatThrownBy(() -> initializer("admin@example.com", "short-pw1").run(null))
			.isInstanceOf(IllegalStateException.class);
	}

}
