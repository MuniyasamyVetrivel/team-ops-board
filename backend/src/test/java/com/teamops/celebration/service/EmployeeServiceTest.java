package com.teamops.celebration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.celebration.dto.CelebrationDtos.EmployeeDto;
import com.teamops.celebration.dto.CelebrationDtos.SaveEmployee;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.EmployeeStatus;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.storage.FileService;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

class EmployeeServiceTest {

	private static final AuthenticatedUser EDITOR = new AuthenticatedUser(5L, "priya.menon@teamops.local",
			"Priya Menon", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CELEBRATIONS_EDIT"));

	private final EmployeeRepository repository = mock(EmployeeRepository.class);

	private final DepartmentRepository departments = mock(DepartmentRepository.class);

	private final UserRepository users = mock(UserRepository.class);

	private final LinkedInPostService linkedInPosts = mock(LinkedInPostService.class);

	private final AuditService audit = mock(AuditService.class);

	private final Department marketing = TestFixtures.department(7L, "DM", "Digital Marketing");

	private EmployeeService service;

	@BeforeEach
	void setUp() {
		service = new EmployeeService(repository, departments, users, mock(FileService.class), linkedInPosts, audit,
				new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-12T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"),
				mock(RecycleBinService.class));
		when(repository.saveAndFlush(any())).thenAnswer(inv -> {
			Employee employee = inv.getArgument(0);
			ReflectionTestUtils.setField(employee, "id", 40L);
			employee.setVersion(0);
			return employee;
		});
		when(users.getReferenceById(any())).thenAnswer(inv -> TestFixtures.user(inv.getArgument(0), "x@teamops.local",
				TestFixtures.role(3L, "EMPLOYEE")));
	}

	@Test
	void onlyEditorsSeeTheBirthYear() {
		Employee priya = employee(EmployeeStatus.ACTIVE);
		when(repository.findDetailedById(40L)).thenReturn(Optional.of(priya));

		EmployeeDto forEmployee = service.get(40L, SliceAuth.EMPLOYEE);
		assertThat(forEmployee.dateOfBirth()).isNull();
		assertThat(forEmployee.birthMonth()).isEqualTo(10);
		assertThat(forEmployee.birthDay()).isEqualTo(14);
		assertThat(forEmployee.nextBirthday()).isEqualTo(LocalDate.of(2026, 10, 14));
		assertThat(forEmployee.canEdit()).isFalse();
		assertThat(forEmployee.nextAnniversary()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(forEmployee.nextAnniversaryYears()).isEqualTo(5);

		assertThat(service.get(40L, EDITOR).dateOfBirth()).isEqualTo(LocalDate.of(1992, 10, 14));
		assertThat(service.get(40L, SliceAuth.SUPER_ADMIN).dateOfBirth()).isEqualTo(LocalDate.of(1992, 10, 14));
	}

	@Test
	void peopleWhoLeftAreOnlyVisibleToEditors() {
		when(repository.findDetailedById(40L)).thenReturn(Optional.of(employee(EmployeeStatus.LEFT)));

		assertError(() -> service.get(40L, SliceAuth.EMPLOYEE), HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND");
		assertThat(service.get(40L, EDITOR).status()).isEqualTo(EmployeeStatus.LEFT);
	}

	@Test
	void aLinkedUserFillsTheBlanks() {
		User rakesh = TestFixtures.user(1L, "rakesh@teamops.local", TestFixtures.role(1L, "SUPER_ADMIN"));
		rakesh.setFirstName("Rakesh");
		rakesh.setLastName("");
		User arun = TestFixtures.user(12L, "arun.kumar@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
		arun.setFirstName("Arun");
		arun.setLastName("Kumar");
		arun.setJobTitle("SEO Executive");
		arun.setReportsTo(rakesh);
		arun.setDepartment(marketing);
		when(users.findWithDepartmentById(12L)).thenReturn(Optional.of(arun));

		EmployeeDto created = service.create(new SaveEmployee("TRI-0102", null, " ", LocalDate.of(1999, 10, 23), null,
				null, LocalDate.of(2023, 10, 14), null, null, "Chennai", 12L, null, null), EDITOR, ClientInfo.unknown());

		assertThat(created.fullName()).isEqualTo("Arun Kumar");
		assertThat(created.designation()).isEqualTo("SEO Executive");
		assertThat(created.reportingManager()).isEqualTo("Rakesh");
		assertThat(created.department().code()).isEqualTo("DM");
		assertThat(created.workEmail()).isEqualTo("arun.kumar@teamops.local");
		assertThat(created.user().id()).isEqualTo(12L);
		verify(audit).record(eq(AuditAction.EMPLOYEE_CREATED), eq(5L), eq("EMPLOYEE"), eq(40L), any(), any());
	}

	@Test
	void requiredFieldsAndAUniqueEmployeeId() {
		assertError(() -> service.create(save("TRI-1", null), EDITOR, ClientInfo.unknown()), HttpStatus.BAD_REQUEST, "FIELD_REQUIRED");

		when(repository.existsByEmployeeCode("TRI-1")).thenReturn(true);
		assertError(() -> service.create(save("TRI-1", "QA Engineer"), EDITOR, ClientInfo.unknown()), HttpStatus.CONFLICT,
				"EMPLOYEE_CODE_TAKEN");

		assertError(() -> service.create(new SaveEmployee("TRI-2", "A", "B", LocalDate.of(2027, 1, 1), "QA", "Rakesh",
				null, null, null, null, null, null, null), EDITOR, ClientInfo.unknown()), HttpStatus.BAD_REQUEST,
				"INVALID_DATE_OF_BIRTH");
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void changingTheBirthDateMovesUpcomingLinkedInPosts() {
		Employee priya = employee(EmployeeStatus.ACTIVE);
		when(repository.findDetailedById(40L)).thenReturn(Optional.of(priya));

		service.update(40L, new SaveEmployee("TRI-0101", "Priya", "Menon", LocalDate.of(1992, 10, 16),
				"Digital Marketing Manager", "Rakesh", LocalDate.of(2021, 10, 20), null, null, null, null, null, 3), EDITOR,
				ClientInfo.unknown());

		verify(linkedInPosts).relocate(eq(OccasionKind.BIRTHDAY), eq(40L), eq(LocalDate.of(2026, 10, 12)), any());
		verify(linkedInPosts, never()).relocate(eq(OccasionKind.WORK_ANNIVERSARY), any(), any(), any());
		verify(audit).record(eq(AuditAction.EMPLOYEE_UPDATED), eq(5L), eq("EMPLOYEE"), eq(40L), any(), any());
	}

	private static SaveEmployee save(String code, String designation) {
		return new SaveEmployee(code, "Rahul", "Verma", LocalDate.of(1996, 11, 22), designation, "Anjali Nair", null,
				null, null, null, null, null, null);
	}

	private static Employee employee(EmployeeStatus status) {
		Employee employee = OccasionFinderTest.employee(40L, "Priya", "Menon", LocalDate.of(1992, 10, 14),
				LocalDate.of(2021, 10, 20));
		employee.setStatus(status);
		employee.setVersion(3);
		return employee;
	}

	private static void assertError(ThrowingCallable call, HttpStatus status, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

}
