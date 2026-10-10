package com.teamops.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.teamops.auth.repository.RefreshTokenRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.AuthenticatedUserFactory;
import com.teamops.common.settings.AppSettingRepository;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.user.dto.CreateUserRequest;
import com.teamops.user.dto.ResetPasswordRequest;
import com.teamops.user.dto.UpdateUserAccessRequest;
import com.teamops.user.dto.UpdateUserRequest;
import com.teamops.user.dto.UserDetail;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.PermissionRepository;
import com.teamops.user.repository.RoleRepository;
import com.teamops.user.repository.UserRepository;

class UserServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private final UserRepository userRepository = mock(UserRepository.class);

	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

	private final RoleRepository roleRepository = mock(RoleRepository.class);

	private final PermissionRepository permissionRepository = mock(PermissionRepository.class);

	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);

	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

	private final AuditService auditService = mock(AuditService.class);

	private final ClientInfo client = new ClientInfo("10.0.0.1", "JUnit");

	private final Role superAdminRole = TestFixtures.role(1L, "SUPER_ADMIN");

	private final Role managerRole = TestFixtures.role(2L, "DEPARTMENT_MANAGER", "TASK_VIEW", "TASK_ASSIGN",
			"REPORT_VIEW");

	private final Role employeeRole = TestFixtures.role(3L, "EMPLOYEE", "TASK_VIEW", "TEAM_VIEW");

	private final Department webDev = TestFixtures.department(5L, "WEBDEV", "Web Development");

	/** USER_MANAGE + PERMISSION_MANAGE + employee permissions, but not a Super Admin. */
	private final AuthenticatedUser hrAdmin = new AuthenticatedUser(50L, "hr@teamops.local", "HR Admin", 3L,
			Set.of("EMPLOYEE"), Set.of("USER_MANAGE", "PERMISSION_MANAGE", "TASK_VIEW", "TEAM_VIEW"));

	private UserService service;

	@BeforeEach
	void setUp() {
		service = new UserService(userRepository, departmentRepository, roleRepository, permissionRepository,
				refreshTokenRepository, passwordEncoder, new AuthenticatedUserFactory(permissionRepository),
				auditService, Clock.fixed(NOW, ZoneOffset.UTC),
				new AppSettingsService(mock(AppSettingRepository.class)));
		when(roleRepository.findByCodeIn(any())).thenAnswer(inv -> rolesFor(inv.getArgument(0)));
		when(permissionRepository.findByCodeIn(any())).thenAnswer(inv -> ((Collection<String>) inv.getArgument(0))
			.stream()
			.map(code -> TestFixtures.permission(code.hashCode(), code))
			.toList());
		when(permissionRepository.findAllCodes()).thenReturn(List.copyOf(SliceAuth.ALL_PERMISSIONS));
		when(departmentRepository.findById(5L)).thenReturn(Optional.of(webDev));
		when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(passwordEncoder.encode(any())).thenReturn("$2a$12$encoded");
	}

	// --- create ---------------------------------------------------------------------------------------------

	@Test
	void createHashesPasswordAppliesDefaultsAndAudits() {
		UserDetail created = service.create(createRequest(Set.of("EMPLOYEE"), Set.of()), SliceAuth.SUPER_ADMIN,
				client);

		assertThat(created.email()).isEqualTo("new.user@teamops.local");
		assertThat(created.weeklyCapacityHours()).isEqualByComparingTo("40");
		assertThat(created.roles()).containsExactly("EMPLOYEE");
		verify(passwordEncoder).encode("Welcome123");
		verify(auditService).record(eq(AuditAction.USER_CREATED), eq(1L), eq("USER"), any(), anyMap(), eq(client));
	}

	@Test
	void createRejectsDuplicateEmail() {
		when(userRepository.existsByEmailIgnoreCase("new.user@teamops.local")).thenReturn(true);

		assertError(() -> service.create(createRequest(Set.of("EMPLOYEE"), Set.of()), SliceAuth.SUPER_ADMIN, client),
				HttpStatus.CONFLICT, "EMAIL_IN_USE");
	}

	@Test
	void createRejectsWeakPassword() {
		CreateUserRequest weak = new CreateUserRequest("a@teamops.local", "password", "A", null, null, null, null,
				null, 5L, null, null, Set.of("EMPLOYEE"), Set.of());

		assertError(() -> service.create(weak, SliceAuth.SUPER_ADMIN, client), HttpStatus.BAD_REQUEST, "WEAK_PASSWORD");
	}

	@Test
	void createRejectsInactiveDepartment() {
		webDev.setStatus(DepartmentStatus.INACTIVE);

		assertError(() -> service.create(createRequest(Set.of("EMPLOYEE"), Set.of()), SliceAuth.SUPER_ADMIN, client),
				HttpStatus.BAD_REQUEST, "DEPARTMENT_INACTIVE");
	}

	@Test
	void onlySuperAdminCanGrantSuperAdmin() {
		assertError(() -> service.create(createRequest(Set.of("SUPER_ADMIN"), Set.of()), hrAdmin, client),
				HttpStatus.FORBIDDEN, "CANNOT_GRANT_SUPER_ADMIN");
	}

	@Test
	void actorCannotGrantRolePermissionsTheyDoNotHold() {
		// DEPARTMENT_MANAGER includes TASK_ASSIGN and REPORT_VIEW, which the HR admin does not have.
		assertThatThrownBy(() -> service.create(createRequest(Set.of("DEPARTMENT_MANAGER"), Set.of()), hrAdmin, client))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("CANNOT_GRANT_PERMISSIONS");
				assertThat(ex.getMessage()).contains("REPORT_VIEW", "TASK_ASSIGN");
			});
	}

	@Test
	void actorCannotGrantDirectPermissionsTheyDoNotHold() {
		assertError(() -> service.create(createRequest(Set.of("EMPLOYEE"), Set.of("MARKETING_VIEW")), hrAdmin, client),
				HttpStatus.FORBIDDEN, "CANNOT_GRANT_PERMISSIONS");
	}

	@Test
	void unknownRoleIsRejected() {
		assertError(() -> service.create(createRequest(Set.of("WIZARD"), Set.of()), SliceAuth.SUPER_ADMIN, client),
				HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE");
	}

	// --- update ---------------------------------------------------------------------------------------------

	@Test
	void updateRejectsReportingLoop() {
		User manager = TestFixtures.user(20L, "manager@teamops.local", managerRole);
		User employee = TestFixtures.user(21L, "employee@teamops.local", employeeRole);
		manager.setReportsTo(employee);
		when(userRepository.findWithAuthoritiesById(21L)).thenReturn(Optional.of(employee));
		when(userRepository.findById(20L)).thenReturn(Optional.of(manager));
		when(departmentRepository.findById(7L)).thenReturn(Optional.of(employee.getDepartment()));

		assertError(() -> service.update(21L, updateRequest(20L), SliceAuth.SUPER_ADMIN, client),
				HttpStatus.BAD_REQUEST, "INVALID_REPORTS_TO");
	}

	@Test
	void updateRejectsReportingToSelf() {
		User employee = TestFixtures.user(21L, "employee@teamops.local", employeeRole);
		when(userRepository.findWithAuthoritiesById(21L)).thenReturn(Optional.of(employee));

		assertError(() -> service.update(21L, updateRequest(21L), SliceAuth.SUPER_ADMIN, client),
				HttpStatus.BAD_REQUEST, "INVALID_REPORTS_TO");
	}

	@Test
	void updateAuditsOnlyChangedFields() {
		User employee = TestFixtures.user(21L, "employee@teamops.local", employeeRole);
		employee.setFirstName("Old");
		employee.setLastName("Name");
		when(userRepository.findWithAuthoritiesById(21L)).thenReturn(Optional.of(employee));

		service.update(21L, updateRequest(null), SliceAuth.SUPER_ADMIN, client);

		verify(auditService).record(eq(AuditAction.USER_UPDATED), eq(1L), eq("USER"), eq(21L),
				argThat(details -> changes(details).keySet().equals(Set.of("firstName", "lastName", "jobTitle"))),
				eq(client));
	}

	// --- access ---------------------------------------------------------------------------------------------

	@Test
	void cannotChangeOwnAccess() {
		assertError(() -> service.updateAccess(1L, new UpdateUserAccessRequest(Set.of("EMPLOYEE"), Set.of()),
				SliceAuth.SUPER_ADMIN, client), HttpStatus.FORBIDDEN, "CANNOT_MODIFY_SELF");
	}

	@Test
	void cannotRemoveTheLastActiveSuperAdmin() {
		User otherAdmin = TestFixtures.user(2L, "admin2@teamops.local", superAdminRole);
		when(userRepository.findWithAuthoritiesById(2L)).thenReturn(Optional.of(otherAdmin));
		when(userRepository.countByRoleAndStatus("SUPER_ADMIN", UserStatus.ACTIVE)).thenReturn(1L);

		assertError(() -> service.updateAccess(2L, new UpdateUserAccessRequest(Set.of("EMPLOYEE"), Set.of()),
				SliceAuth.SUPER_ADMIN, client), HttpStatus.CONFLICT, "LAST_SUPER_ADMIN");
	}

	@Test
	void accessChangeIsAuditedWithBeforeAndAfter() {
		User employee = TestFixtures.user(21L, "employee@teamops.local", employeeRole);
		when(userRepository.findWithAuthoritiesById(21L)).thenReturn(Optional.of(employee));

		UserDetail result = service.updateAccess(21L,
				new UpdateUserAccessRequest(Set.of("EMPLOYEE"), Set.of("MARKETING_VIEW", "SEO_VIEW")),
				SliceAuth.SUPER_ADMIN, client);

		assertThat(result.directPermissions()).containsExactly("MARKETING_VIEW", "SEO_VIEW");
		assertThat(result.effectivePermissions()).contains("MARKETING_VIEW", "TASK_VIEW");
		verify(auditService).record(eq(AuditAction.USER_ACCESS_CHANGED), eq(1L), eq("USER"), eq(21L),
				argThat(details -> details.containsKey("before") && details.containsKey("after")), eq(client));
	}

	@Test
	void unchangedAccessIsNotAudited() {
		User employee = TestFixtures.user(21L, "employee@teamops.local", employeeRole);
		when(userRepository.findWithAuthoritiesById(21L)).thenReturn(Optional.of(employee));

		service.updateAccess(21L, new UpdateUserAccessRequest(Set.of("EMPLOYEE"), Set.of()), SliceAuth.SUPER_ADMIN,
				client);

		verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
	}

	@Test
	void keepingExistingGrantsTheActorLacksIsAllowed() {
		// The target already has MARKETING_VIEW; the HR admin lacks it but is only adding TEAM_VIEW-level access.
		User employee = TestFixtures.user(21L, "employee@teamops.local", employeeRole, "MARKETING_VIEW");
		when(userRepository.findWithAuthoritiesById(21L)).thenReturn(Optional.of(employee));

		UserDetail result = service.updateAccess(21L,
				new UpdateUserAccessRequest(Set.of("EMPLOYEE"), Set.of("MARKETING_VIEW", "TEAM_VIEW")), hrAdmin,
				client);

		assertThat(result.directPermissions()).containsExactly("MARKETING_VIEW", "TEAM_VIEW");
	}

	// --- status & password ----------------------------------------------------------------------------------

	@Test
	void disablingRevokesAllSessionsAndAudits() {
		User employee = TestFixtures.user(21L, "employee@teamops.local", employeeRole);
		when(userRepository.findWithAuthoritiesById(21L)).thenReturn(Optional.of(employee));

		UserDetail result = service.setStatus(21L, UserStatus.DISABLED, SliceAuth.SUPER_ADMIN, client);

		assertThat(result.status()).isEqualTo(UserStatus.DISABLED);
		verify(refreshTokenRepository).revokeAllActiveForUser(21L, NOW);
		verify(auditService).record(eq(AuditAction.USER_DISABLED), eq(1L), eq("USER"), eq(21L), anyMap(), eq(client));
	}

	@Test
	void cannotDisableSelf() {
		assertError(() -> service.setStatus(1L, UserStatus.DISABLED, SliceAuth.SUPER_ADMIN, client),
				HttpStatus.FORBIDDEN, "CANNOT_MODIFY_SELF");
	}

	@Test
	void nonSuperAdminCannotDisableASuperAdmin() {
		User admin = TestFixtures.user(2L, "admin2@teamops.local", superAdminRole);
		when(userRepository.findWithAuthoritiesById(2L)).thenReturn(Optional.of(admin));

		assertError(() -> service.setStatus(2L, UserStatus.DISABLED, hrAdmin, client), HttpStatus.FORBIDDEN,
				"SUPER_ADMIN_PROTECTED");
		verify(refreshTokenRepository, never()).revokeAllActiveForUser(anyLong(), any());
	}

	@Test
	void enablingAnActiveUserIsANoOp() {
		User employee = TestFixtures.user(21L, "employee@teamops.local", employeeRole);
		when(userRepository.findWithAuthoritiesById(21L)).thenReturn(Optional.of(employee));

		service.setStatus(21L, UserStatus.ACTIVE, SliceAuth.SUPER_ADMIN, client);

		verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
	}

	@Test
	void resetPasswordReEncodesAndSignsOutEverywhere() {
		User employee = TestFixtures.user(21L, "employee@teamops.local", employeeRole);
		when(userRepository.findWithAuthoritiesById(21L)).thenReturn(Optional.of(employee));

		service.resetPassword(21L, new ResetPasswordRequest("NewSecret99"), SliceAuth.SUPER_ADMIN, client);

		assertThat(employee.getPasswordHash()).isEqualTo("$2a$12$encoded");
		verify(refreshTokenRepository).revokeAllActiveForUser(21L, NOW);
		verify(auditService).record(eq(AuditAction.USER_PASSWORD_RESET), eq(1L), eq("USER"), eq(21L),
				argThat(details -> !details.toString().contains("NewSecret99")), eq(client));
	}

	@Test
	void systemCallerSkipsActorChecks() {
		UserDetail created = service.create(createRequest(Set.of("SUPER_ADMIN"), Set.of()), null, client);

		assertThat(created.roles()).containsExactly("SUPER_ADMIN");
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private List<Role> rolesFor(Collection<String> codes) {
		return List.of(superAdminRole, managerRole, employeeRole)
			.stream()
			.filter(role -> codes.contains(role.getCode()))
			.toList();
	}

	private static CreateUserRequest createRequest(Set<String> roles, Set<String> permissions) {
		return new CreateUserRequest(" New.User@TeamOps.local ", "Welcome123", "New", "User", "Developer", null, null,
				null, 5L, null, null, roles, permissions);
	}

	private static UpdateUserRequest updateRequest(Long reportsToId) {
		return new UpdateUserRequest("employee@teamops.local", "New", "Surname", "Lead", null, null, null, 7L,
				reportsToId, new BigDecimal("40"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> changes(Map<String, ?> details) {
		return (Map<String, Object>) details.get("changes");
	}

	private static void assertError(ThrowingCallable call, HttpStatus status, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

}
