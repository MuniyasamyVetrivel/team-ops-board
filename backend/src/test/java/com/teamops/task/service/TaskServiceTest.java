package com.teamops.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.project.repository.ProjectRepository;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.tag.TagService;
import com.teamops.task.dto.DueState;
import com.teamops.task.dto.TaskDetail;
import com.teamops.task.dto.TaskRequests;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskHistory;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.event.TaskAssignedEvent;
import com.teamops.task.repository.TaskAttachmentRepository;
import com.teamops.task.repository.TaskChecklistItemRepository;
import com.teamops.task.repository.TaskCommentRepository;
import com.teamops.task.repository.TaskHistoryRepository;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

class TaskServiceTest {

	/** 10:00 UTC = 15:30 in Asia/Kolkata on 7 October 2026. */
	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	private final TaskRepository taskRepository = mock(TaskRepository.class);

	private final TaskHistoryRepository historyRepository = mock(TaskHistoryRepository.class);

	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

	private final UserRepository userRepository = mock(UserRepository.class);

	private final TagService tagService = mock(TagService.class);

	private final CodeGenerator codeGenerator = mock(CodeGenerator.class);

	private final AccessScopeService scopes = mock(AccessScopeService.class);

	private final AuditService auditService = mock(AuditService.class);

	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

	private final ClientInfo client = ClientInfo.unknown();

	private final Department webDev = TestFixtures.department(5L, "WEBDEV", "Web Development");

	private final Department marketing = TestFixtures.department(7L, "DM", "Digital Marketing");

	private final AuthenticatedUser employee = SliceAuth.EMPLOYEE;

	private final User karthik = user(4L, webDev);

	private TaskService service;

	@BeforeEach
	void setUp() {
		service = new TaskService(taskRepository, mock(TaskCommentRepository.class),
				mock(TaskChecklistItemRepository.class), historyRepository, mock(TaskAttachmentRepository.class),
				departmentRepository, mock(ProjectRepository.class), userRepository, tagService, codeGenerator, scopes,
				auditService, new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"), events);
		when(scopes.scopeFor(employee)).thenReturn(AccessScope.own(employee.id()));
		when(scopes.scopeFor(SliceAuth.SUPER_ADMIN)).thenReturn(AccessScope.all(1L));
		when(departmentRepository.findById(5L)).thenReturn(Optional.of(webDev));
		when(departmentRepository.findById(7L)).thenReturn(Optional.of(marketing));
		when(userRepository.getReferenceById(any())).thenAnswer(inv -> user(inv.getArgument(0), webDev));
		when(userRepository.findWithDepartmentById(4L)).thenReturn(Optional.of(karthik));
		when(tagService.resolve(any())).thenReturn(Set.of());
		when(codeGenerator.next(CodeGenerator.TASK)).thenReturn("TSK-000042");
		when(taskRepository.save(any())).thenAnswer(inv -> {
			Task task = inv.getArgument(0);
			ReflectionTestUtils.setField(task, "id", 42L);
			return task;
		});
	}

	@Test
	void createAssignsCodeDefaultsAndAudits() {
		TaskDetail detail = service.create(new TaskRequests.CreateTask("  Fix header  ", null, null, null, 4L, null,
				null, TODAY, new BigDecimal("3"), List.of()), employee, client);

		assertThat(detail.code()).isEqualTo("TSK-000042");
		assertThat(detail.title()).isEqualTo("Fix header");
		assertThat(detail.department().code()).as("defaults to the creator's department").isEqualTo("WEBDEV");
		assertThat(detail.priority()).isEqualTo(TaskPriority.MEDIUM);
		assertThat(detail.dueState()).isEqualTo(DueState.DUE_TODAY);
		verify(historyRepository).save(argThat((TaskHistory h) -> h.getFieldName().equals("created")));
		verify(auditService).record(eq(AuditAction.TASK_CREATED), eq(4L), eq("TASK"), eq(42L), anyMap(), eq(client));
	}

	@Test
	void employeeCannotCreateInAnotherDepartment() {
		assertError(() -> service.create(new TaskRequests.CreateTask("x", null, 7L, null, null, null, null, null, null,
				null), employee, client), HttpStatus.FORBIDDEN, "FORBIDDEN");
	}

	@Test
	void employeeCannotAssignSomeoneElseOnCreate() {
		User colleague = user(41L, webDev);
		when(userRepository.findWithDepartmentById(41L)).thenReturn(Optional.of(colleague));

		assertError(() -> service.create(new TaskRequests.CreateTask("x", null, null, null, 41L, null, null, null, null,
				null), employee, client), HttpStatus.FORBIDDEN, "CANNOT_ASSIGN");
	}

	@Test
	void dueDateBeforeStartDateIsRejected() {
		assertError(() -> service.create(new TaskRequests.CreateTask("x", null, null, null, null, null, TODAY,
				TODAY.minusDays(1), null, null), employee, client), HttpStatus.BAD_REQUEST, "INVALID_DATES");
	}

	@Test
	void completingStampsCompletedAtAndReopeningClearsIt() {
		Task task = existing(TaskStatus.IN_PROGRESS);

		TaskDetail completed = service.changeStatus(42L, TaskStatus.COMPLETED, employee, client);
		assertThat(completed.completedAt()).isEqualTo(NOW);
		assertThat(completed.dueState()).isEqualTo(DueState.NONE);

		TaskDetail reopened = service.changeStatus(42L, TaskStatus.TODO, employee, client);
		assertThat(reopened.completedAt()).isNull();
		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
		verify(historyRepository).save(argThat((TaskHistory h) -> h.getFieldName().equals("reopened")
				&& "COMPLETED".equals(h.getOldValue()) && "TODO".equals(h.getNewValue())));
		verify(auditService, org.mockito.Mockito.times(2)).record(eq(AuditAction.TASK_STATUS_CHANGED), eq(4L),
				eq("TASK"), eq(42L), anyMap(), eq(client));
	}

	@Test
	void cancellingNeedsTaskDelete() {
		existing(TaskStatus.TODO);

		assertError(() -> service.changeStatus(42L, TaskStatus.CANCELLED, employee, client), HttpStatus.FORBIDDEN,
				"CANNOT_CANCEL");
		assertThat(service.changeStatus(42L, TaskStatus.CANCELLED, SliceAuth.SUPER_ADMIN, client).status())
			.isEqualTo(TaskStatus.CANCELLED);
	}

	@Test
	void invisibleTasksLookLikeTheyDoNotExist() {
		Task other = existing(TaskStatus.TODO);
		other.setAssignee(user(99L, marketing));
		other.setDepartment(marketing);

		assertError(() -> service.get(42L, employee), HttpStatus.NOT_FOUND, "TASK_NOT_FOUND");
	}

	@Test
	void staleVersionIsRejected() {
		existing(TaskStatus.TODO);

		assertError(() -> service.update(42L, update(3), employee), HttpStatus.CONFLICT, "STALE_UPDATE");
	}

	@Test
	void updateRecordsOnlyChangedFields() {
		Task task = existing(TaskStatus.TODO);

		service.update(42L, update(0), employee);

		assertThat(task.getPriority()).isEqualTo(TaskPriority.URGENT);
		verify(historyRepository).save(argThat((TaskHistory h) -> h.getFieldName().equals("priority")
				&& "MEDIUM".equals(h.getOldValue()) && "URGENT".equals(h.getNewValue())));
		verify(historyRepository).save(argThat((TaskHistory h) -> h.getFieldName().equals("dueDate")));
		verify(historyRepository, never()).save(argThat((TaskHistory h) -> h.getFieldName().equals("title")));
	}

	@Test
	void reassigningIsAudited() {
		existing(TaskStatus.TODO);
		User colleague = user(41L, webDev);
		when(userRepository.findWithDepartmentById(41L)).thenReturn(Optional.of(colleague));

		service.assign(42L, 41L, SliceAuth.SUPER_ADMIN, client);

		verify(auditService).record(eq(AuditAction.TASK_ASSIGNED), eq(1L), eq("TASK"), eq(42L), anyMap(), eq(client));
		verify(historyRepository).save(argThat((TaskHistory h) -> h.getFieldName().equals("assignee")));
		verify(events).publishEvent(new TaskAssignedEvent(42L, "TSK-000042", "Fix header", 41L, 1L, "Rakesh"));
	}

	@Test
	void selfAssignmentPublishesNoAssignmentEvent() {
		service.create(new TaskRequests.CreateTask("Fix header", null, null, null, 4L, null, null, null, null,
				List.of()), employee, client);
		Task unassigned = existing(TaskStatus.TODO);
		unassigned.setAssignee(null);
		unassigned.setCreatedBy(karthik);
		service.assign(42L, 4L, employee, client);

		verify(events, never()).publishEvent(any(Object.class));
	}

	@Test
	void creatingForSomeoneElsePublishesAnAssignmentEvent() {
		User colleague = user(41L, webDev);
		when(userRepository.findWithDepartmentById(41L)).thenReturn(Optional.of(colleague));

		service.create(new TaskRequests.CreateTask("Fix header", null, 5L, null, 41L, null, null, null, null,
				List.of()), SliceAuth.SUPER_ADMIN, client);

		verify(events).publishEvent(new TaskAssignedEvent(42L, "TSK-000042", "Fix header", 41L, 1L, "Rakesh"));
	}

	private Task existing(TaskStatus status) {
		Task task = new Task();
		ReflectionTestUtils.setField(task, "id", 42L);
		task.setCode("TSK-000042");
		task.setTitle("Fix header");
		task.setDepartment(webDev);
		task.setAssignee(karthik);
		task.setStatus(status);
		task.setVersion(0);
		when(taskRepository.findDetailedById(42L)).thenReturn(Optional.of(task));
		return task;
	}

	private static TaskRequests.UpdateTask update(int version) {
		return new TaskRequests.UpdateTask(version, "Fix header", null, 5L, null, TaskPriority.URGENT, null,
				TODAY.plusDays(2), null, null, List.of());
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
