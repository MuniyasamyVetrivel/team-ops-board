package com.teamops.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

import com.teamops.approval.service.ApprovalService;
import com.teamops.calendar.dto.CalendarDtos.EventDetail;
import com.teamops.calendar.dto.CalendarDtos.SaveEvent;
import com.teamops.calendar.entity.CalendarEvent;
import com.teamops.calendar.entity.CalendarEventType;
import com.teamops.calendar.repository.CalendarEventRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.project.repository.ProjectMilestoneRepository;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

class CalendarServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

	private static final AuthenticatedUser MANAGER = new AuthenticatedUser(3L, "sanjay.varma@teamops.local",
			"Sanjay Varma", 5L, Set.of("DEPARTMENT_MANAGER"),
			Set.of("CALENDAR_VIEW", "CALENDAR_EDIT", "TASK_VIEW", "DASHBOARD_VIEW"));

	private final CalendarEventRepository eventRepository = mock(CalendarEventRepository.class);

	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

	private final UserRepository userRepository = mock(UserRepository.class);

	private final AccessScopeService scopes = mock(AccessScopeService.class);

	private final Department webDev = TestFixtures.department(5L, "WEBDEV", "Web Development");

	private final Department marketing = TestFixtures.department(7L, "DM", "Digital Marketing");

	private CalendarService service;

	@BeforeEach
	void setUp() {
		service = new CalendarService(eventRepository, mock(TaskRepository.class), departmentRepository,
				userRepository, scopes, mock(ProjectMilestoneRepository.class), mock(ApprovalService.class),
				new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"));
		when(scopes.scopeFor(MANAGER)).thenReturn(AccessScope.departments(3L, Set.of(5L)));
		when(scopes.scopeFor(SliceAuth.EMPLOYEE)).thenReturn(AccessScope.own(4L));
		when(scopes.scopeFor(SliceAuth.SUPER_ADMIN)).thenReturn(AccessScope.all(1L));
		when(scopes.canManageDepartment(MANAGER, 5L)).thenReturn(true);
		when(scopes.canManageDepartment(MANAGER, 7L)).thenReturn(false);
		when(departmentRepository.findById(5L)).thenReturn(Optional.of(webDev));
		when(departmentRepository.findById(7L)).thenReturn(Optional.of(marketing));
		when(userRepository.getReferenceById(any())).thenAnswer(inv -> user(inv.getArgument(0), webDev));
		when(eventRepository.save(any())).thenAnswer(inv -> {
			CalendarEvent event = inv.getArgument(0);
			ReflectionTestUtils.setField(event, "id", 11L);
			return event;
		});
	}

	@Test
	void allDayEventsSpanBusinessZoneMidnights() {
		EventDetail detail = service.create(allDay(CalendarEventType.TEAM_EVENT, 5L, null), MANAGER);

		assertThat(detail.startAt()).as("00:00 IST").isEqualTo(Instant.parse("2026-10-14T18:30:00Z"));
		assertThat(detail.endAt()).as("midnight after the last day").isEqualTo(Instant.parse("2026-10-16T18:30:00Z"));
		assertThat(detail.startDate()).isEqualTo(LocalDate.of(2026, 10, 15));
		assertThat(detail.endDate()).as("inclusive").isEqualTo(LocalDate.of(2026, 10, 16));
		assertThat(detail.canEdit()).isTrue();
	}

	@Test
	void onlySuperAdminCreatesCompanyWideEvents() {
		assertError(() -> service.create(allDay(CalendarEventType.IMPORTANT_DATE, null, null), MANAGER),
				HttpStatus.FORBIDDEN, "FORBIDDEN");
		assertThat(service.create(allDay(CalendarEventType.IMPORTANT_DATE, null, null), SliceAuth.SUPER_ADMIN)
			.department()).isNull();
	}

	@Test
	void managersCannotCreateEventsForOtherDepartments() {
		assertError(() -> service.create(allDay(CalendarEventType.MEETING, 7L, null), MANAGER), HttpStatus.FORBIDDEN,
				"FORBIDDEN");
	}

	@Test
	void leaveNeedsAPersonAndIsFiledUnderTheirDepartment() {
		assertError(() -> service.create(allDay(CalendarEventType.LEAVE, 5L, null), MANAGER), HttpStatus.BAD_REQUEST,
				"LEAVE_NEEDS_USER");

		User karthik = user(4L, webDev);
		when(userRepository.findWithDepartmentById(4L)).thenReturn(Optional.of(karthik));
		when(scopes.canViewWorkOf(MANAGER, 4L, 5L)).thenReturn(true);

		EventDetail leave = service.create(allDay(CalendarEventType.LEAVE, 7L, 4L), MANAGER);
		assertThat(leave.department().code()).isEqualTo("WEBDEV");
		assertThat(leave.user().id()).isEqualTo(4L);
	}

	@Test
	void timedEventsMustEndAfterTheyStart() {
		Instant start = Instant.parse("2026-10-15T05:00:00Z");
		assertError(() -> service.create(new SaveEvent("Standup", null, CalendarEventType.MEETING, false, null, null,
				start, start, 5L, null, null), MANAGER), HttpStatus.BAD_REQUEST, "INVALID_DATES");
	}

	@Test
	void eventsOutsideTheViewersDepartmentsLookMissing() {
		CalendarEvent event = event(marketing);
		when(eventRepository.findDetailedById(11L)).thenReturn(Optional.of(event));

		assertError(() -> service.get(11L, SliceAuth.EMPLOYEE), HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND");
		assertThat(CalendarService.canView(event(null), AccessScope.own(4L), SliceAuth.EMPLOYEE))
			.as("company-wide").isTrue();
		assertThat(CalendarService.canView(event(webDev), AccessScope.own(4L), SliceAuth.EMPLOYEE))
			.as("own department").isTrue();
		assertThat(CalendarService.canView(event(marketing), AccessScope.all(1L), SliceAuth.SUPER_ADMIN)).isTrue();
	}

	@Test
	void staleUpdatesAreRejected() {
		CalendarEvent event = event(webDev);
		event.setVersion(2);
		when(eventRepository.findDetailedById(11L)).thenReturn(Optional.of(event));

		assertError(() -> service.update(11L, allDay(CalendarEventType.TEAM_EVENT, 5L, null), MANAGER),
				HttpStatus.CONFLICT, "STALE_UPDATE");
	}

	private static SaveEvent allDay(CalendarEventType type, Long departmentId, Long userId) {
		return new SaveEvent("Offsite", null, type, true, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16),
				null, null, departmentId, userId, null);
	}

	private static CalendarEvent event(Department department) {
		CalendarEvent event = new CalendarEvent();
		ReflectionTestUtils.setField(event, "id", 11L);
		event.setTitle("Offsite");
		event.setEventType(CalendarEventType.TEAM_EVENT);
		event.setDepartment(department);
		event.setAllDay(true);
		event.setStartAt(Instant.parse("2026-10-14T18:30:00Z"));
		event.setEndAt(Instant.parse("2026-10-15T18:30:00Z"));
		event.setVersion(0);
		return event;
	}

	private static User user(long id, Department department) {
		User user = TestFixtures.user(id, "user" + id + "@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
		user.setDepartment(department);
		return user;
	}

	private static void assertError(ThrowingCallable call, HttpStatus status, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

}
