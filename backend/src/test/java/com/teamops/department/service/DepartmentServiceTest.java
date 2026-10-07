package com.teamops.department.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.dto.CreateDepartmentRequest;
import com.teamops.department.dto.UpdateDepartmentRequest;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentMember;
import com.teamops.department.entity.DepartmentMemberRole;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentMemberRepository;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

class DepartmentServiceTest {

	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

	private final DepartmentMemberRepository memberRepository = mock(DepartmentMemberRepository.class);

	private final UserRepository userRepository = mock(UserRepository.class);

	private final AccessScopeService accessScopeService = mock(AccessScopeService.class);

	private final AuditService auditService = mock(AuditService.class);

	private final ClientInfo client = ClientInfo.unknown();

	private final DepartmentService service = new DepartmentService(departmentRepository, memberRepository,
			userRepository, accessScopeService, auditService);

	private final Department marketing = TestFixtures.department(7L, "DM", "Digital Marketing");

	/** Department manager of Digital Marketing, without DEPARTMENT_MANAGE. */
	private final AuthenticatedUser dmManager = new AuthenticatedUser(30L, "priya@teamops.local", "Priya", 7L,
			Set.of("DEPARTMENT_MANAGER"), Set.of("TEAM_VIEW"));

	private User designer;

	@BeforeEach
	void setUp() {
		when(departmentRepository.findWithManagerById(7L)).thenReturn(Optional.of(marketing));
		designer = TestFixtures.user(40L, "pooja@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
		designer.setDepartment(TestFixtures.department(9L, "GRAPHICS", "Graphic & Media"));
		when(userRepository.findWithDepartmentById(40L)).thenReturn(Optional.of(designer));
		when(memberRepository.findById(any())).thenReturn(Optional.empty());
		when(accessScopeService.canManageDepartment(dmManager, 7L)).thenReturn(true);
		when(accessScopeService.canManageDepartment(SliceAuth.SUPER_ADMIN, 7L)).thenReturn(true);
	}

	@Test
	void createNormalisesCodeAndAudits() {
		when(departmentRepository.save(any())).thenAnswer(inv -> {
			Department saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 11L);
			return saved;
		});
		when(departmentRepository.findWithManagerById(11L)).thenAnswer(inv -> Optional.of(TestFixtures.department(11L, "QA", "Quality")));

		service.create(new CreateDepartmentRequest(" Quality ", "qa", null, null), SliceAuth.SUPER_ADMIN, client);

		verify(departmentRepository).save(org.mockito.ArgumentMatchers
			.argThat(d -> d.getCode().equals("QA") && d.getName().equals("Quality")));
		verify(auditService).record(eq(AuditAction.DEPARTMENT_CREATED), eq(1L), eq("DEPARTMENT"), eq(11L), anyMap(),
				eq(client));
	}

	@Test
	void createRejectsDuplicateCode() {
		when(departmentRepository.existsByCodeIgnoreCase("DM")).thenReturn(true);

		assertError(() -> service.create(new CreateDepartmentRequest("Marketing 2", "dm", null, null),
				SliceAuth.SUPER_ADMIN, client), HttpStatus.CONFLICT, "DEPARTMENT_CODE_IN_USE");
	}

	@Test
	void cannotDeactivateDepartmentWithActiveUsers() {
		when(userRepository.countByDepartmentIdAndStatus(7L, UserStatus.ACTIVE)).thenReturn(3L);

		assertError(() -> service.update(7L,
				new UpdateDepartmentRequest("Digital Marketing", null, null, DepartmentStatus.INACTIVE),
				SliceAuth.SUPER_ADMIN, client), HttpStatus.CONFLICT, "DEPARTMENT_HAS_ACTIVE_USERS");
	}

	@Test
	void managerCannotBeADisabledUser() {
		User disabled = TestFixtures.user(41L, "gone@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
		disabled.setStatus(UserStatus.DISABLED);
		when(userRepository.findById(41L)).thenReturn(Optional.of(disabled));

		assertError(() -> service.update(7L,
				new UpdateDepartmentRequest("Digital Marketing", null, 41L, DepartmentStatus.ACTIVE),
				SliceAuth.SUPER_ADMIN, client), HttpStatus.BAD_REQUEST, "USER_DISABLED");
	}

	@Test
	void departmentManagerCanAddMembersToTheirDepartment() {
		when(userRepository.findByDepartmentIdOrderByFirstNameAscLastNameAsc(7L)).thenReturn(List.of());
		when(memberRepository.findByIdDepartmentId(7L)).thenReturn(List.of());

		service.upsertMember(7L, 40L, DepartmentMemberRole.MEMBER, dmManager, client);

		verify(memberRepository).save(org.mockito.ArgumentMatchers
			.argThat((DepartmentMember m) -> m.getMemberRole() == DepartmentMemberRole.MEMBER));
		verify(auditService).record(eq(AuditAction.DEPARTMENT_MEMBER_ADDED), eq(30L), eq("DEPARTMENT"), eq(7L),
				anyMap(), eq(client));
	}

	@Test
	void departmentManagerCannotGrantManagerMembership() {
		assertError(() -> service.upsertMember(7L, 40L, DepartmentMemberRole.MANAGER, dmManager, client),
				HttpStatus.FORBIDDEN, "CANNOT_ASSIGN_MANAGER");
		verify(memberRepository, never()).save(any());
	}

	@Test
	void outsiderCannotManageMembers() {
		AuthenticatedUser outsider = SliceAuth.EMPLOYEE;
		when(accessScopeService.canManageDepartment(outsider, 7L)).thenReturn(false);

		assertError(() -> service.upsertMember(7L, 40L, DepartmentMemberRole.MEMBER, outsider, client),
				HttpStatus.FORBIDDEN, "FORBIDDEN");
	}

	@Test
	void primaryDepartmentCannotAlsoBeASecondaryMembership() {
		designer.setDepartment(marketing);

		assertError(() -> service.upsertMember(7L, 40L, DepartmentMemberRole.MEMBER, SliceAuth.SUPER_ADMIN, client),
				HttpStatus.BAD_REQUEST, "ALREADY_PRIMARY_MEMBER");
	}

	@Test
	void disabledUsersCannotBeAdded() {
		designer.setStatus(UserStatus.DISABLED);

		assertError(() -> service.upsertMember(7L, 40L, DepartmentMemberRole.MEMBER, SliceAuth.SUPER_ADMIN, client),
				HttpStatus.BAD_REQUEST, "USER_DISABLED");
	}

	@Test
	void assignManagerIfUnsetKeepsAnExistingManager() {
		User existing = TestFixtures.user(30L, "priya@teamops.local", TestFixtures.role(2L, "DEPARTMENT_MANAGER"));
		marketing.setManager(existing);

		service.assignManagerIfUnset(7L, 99L);

		assertThat(marketing.getManager()).isSameAs(existing);
	}

	private static void assertError(ThrowingCallable call, HttpStatus status, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

}
