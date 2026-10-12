package com.teamops.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.persistence.SpecificationAggregates;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.department.entity.Department;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.repository.TaskRepository;
import com.teamops.team.dto.WorkHistoryDtos.TaskRow;
import com.teamops.team.service.WorkHistoryService.Filter;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

class WorkHistoryServiceTest {

	/** 12 October 2026, 11:30 in India. */
	private static final Instant NOW = Instant.parse("2026-10-12T06:00:00Z");

	private static final Filter NO_FILTER = new Filter(null, null, null, null, null, null, null);

	private final UserRepository users = mock(UserRepository.class);

	private final AccessScopeService scopes = mock(AccessScopeService.class);

	private final TaskRepository tasks = mock(TaskRepository.class);

	private final TicketRepository tickets = mock(TicketRepository.class);

	private final Department webDev = TestFixtures.department(5L, "WEBDEV", "Web Development");

	private WorkHistoryService service;

	@BeforeEach
	void setUp() {
		service = new WorkHistoryService(users, scopes, tasks, tickets, mock(SpecificationAggregates.class),
				new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"));
		User karthik = TestFixtures.user(4L, "karthik.raj@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
		karthik.setDepartment(webDev);
		when(users.findWithDepartmentById(4L)).thenReturn(Optional.of(karthik));
		when(scopes.scopeFor(any())).thenReturn(AccessScope.all(1L));
	}

	@Test
	void onlyPeopleInTheViewersScope() {
		when(scopes.canViewWorkOf(SliceAuth.EMPLOYEE, 4L, 5L)).thenReturn(false);
		assertError(() -> service.tasks(4L, NO_FILTER, PageRequest.of(0, 25), SliceAuth.EMPLOYEE), HttpStatus.FORBIDDEN,
				"WORK_NOT_VISIBLE");
		assertError(() -> service.ticketSummary(4L, NO_FILTER, SliceAuth.EMPLOYEE), HttpStatus.FORBIDDEN,
				"WORK_NOT_VISIBLE");
		assertError(() -> service.tasks(99L, NO_FILTER, PageRequest.of(0, 25), SliceAuth.SUPER_ADMIN), HttpStatus.NOT_FOUND,
				"USER_NOT_FOUND");
		verifyNoInteractions(tasks, tickets);
	}

	@Test
	void rowsSayWhetherWorkWasOnTimeOrHowLate() {
		when(scopes.canViewWorkOf(SliceAuth.SUPER_ADMIN, 4L, 5L)).thenReturn(true);
		Task onTime = task(1L, TaskStatus.COMPLETED, LocalDate.of(2026, 9, 20), Instant.parse("2026-09-20T12:00:00Z"));
		Task late = task(2L, TaskStatus.COMPLETED, LocalDate.of(2026, 9, 20), Instant.parse("2026-09-23T04:00:00Z"));
		Task overdue = task(3L, TaskStatus.IN_PROGRESS, LocalDate.of(2026, 10, 9), null);
		Task undated = task(4L, TaskStatus.COMPLETED, null, Instant.parse("2026-09-23T04:00:00Z"));
		when(tasks.findAll(ArgumentMatchers.<Specification<Task>>any(), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(onTime, late, overdue, undated)));

		List<TaskRow> rows = service.tasks(4L, NO_FILTER, PageRequest.of(0, 25), SliceAuth.SUPER_ADMIN).content();

		assertThat(rows).extracting(TaskRow::id, TaskRow::completedDate, TaskRow::overdue, TaskRow::onTime, TaskRow::daysLate)
			.containsExactly(tuple(1L, LocalDate.of(2026, 9, 20), false, true, null),
					tuple(2L, LocalDate.of(2026, 9, 23), false, false, 3), tuple(3L, null, true, null, 3),
					tuple(4L, LocalDate.of(2026, 9, 23), false, null, null));
	}

	@Test
	void filtersAreValidated() {
		when(scopes.canViewWorkOf(SliceAuth.SUPER_ADMIN, 4L, 5L)).thenReturn(true);
		assertError(() -> service.tasks(4L, new Filter(Set.of("DONE"), null, null, null, null, null, null),
				PageRequest.of(0, 25), SliceAuth.SUPER_ADMIN), HttpStatus.BAD_REQUEST, "INVALID_FILTER");
		assertError(() -> service.tasks(4L, new Filter(null, null, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 10),
				null, null, null), PageRequest.of(0, 25), SliceAuth.SUPER_ADMIN), HttpStatus.BAD_REQUEST, "INVALID_RANGE");
	}

	private Task task(Long id, TaskStatus status, LocalDate due, Instant completedAt) {
		Task task = new Task();
		ReflectionTestUtils.setField(task, "id", id);
		task.setCode("TSK-00000" + id);
		task.setTitle("Task " + id);
		task.setDepartment(webDev);
		task.setPriority(TaskPriority.HIGH);
		task.setStatus(status);
		task.setDueDate(due);
		task.setCompletedAt(completedAt);
		task.setActualHours(new BigDecimal("2.5"));
		return task;
	}

	private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status,
			String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

}
