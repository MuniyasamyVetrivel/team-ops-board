package com.teamops.calendar.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.approval.entity.Approval;
import com.teamops.approval.service.ApprovalService;
import com.teamops.calendar.dto.CalendarDtos.CalendarItem;
import com.teamops.calendar.dto.CalendarDtos.CalendarResponse;
import com.teamops.calendar.dto.CalendarDtos.EventDetail;
import com.teamops.calendar.dto.CalendarDtos.ItemKind;
import com.teamops.calendar.dto.CalendarDtos.SaveEvent;
import com.teamops.calendar.dto.CalendarDtos.TaskInfo;
import com.teamops.calendar.entity.CalendarEvent;
import com.teamops.calendar.entity.CalendarEventType;
import com.teamops.calendar.repository.CalendarEventRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectMilestone;
import com.teamops.project.repository.ProjectMilestoneRepository;
import com.teamops.project.service.ProjectAccess;
import com.teamops.task.dto.DueState;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.repository.TaskRepository;
import com.teamops.task.repository.TaskSpecifications;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * The calendar merges stored events with task deadlines, open project milestones and pending approval due dates at
 * query time (brief section 19). Each follows its own module's visibility rules. Events are visible when company-wide, in the viewer's own or managed departments, about the viewer,
 * or created by them. Events the viewer cannot see are reported as not found.
 * <p>
 * Changing an event needs CALENDAR_EDIT plus: Super Admin for company-wide events, department management
 * ({@link AccessScopeService#canManageDepartment}) for department events, and scope over the person for leave. The
 * creator may always change their own event.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

	static final int MAX_RANGE_DAYS = 100;

	static final int MAX_TASKS = 1000;

	private static final Set<TaskStatus> NOT_CANCELLED = Set.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS,
			TaskStatus.BLOCKED, TaskStatus.IN_REVIEW, TaskStatus.COMPLETED);

	private final CalendarEventRepository eventRepository;

	private final TaskRepository taskRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final AccessScopeService accessScopeService;

	private final ProjectMilestoneRepository milestoneRepository;

	private final ApprovalService approvalService;

	private final BusinessCalendar calendar;

	/**
	 * @param mine only the viewer's own deadlines (My Calendar); events are unchanged
	 */
	@Transactional(readOnly = true)
	public CalendarResponse range(LocalDate from, LocalDate to, boolean mine, AuthenticatedUser actor) {
		if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw ApiException.badRequest("INVALID_RANGE",
					"'to' must be on or after 'from' and the range at most " + MAX_RANGE_DAYS + " days");
		}
		AccessScope scope = accessScopeService.scopeFor(actor);
		LocalDate today = calendar.today();
		List<CalendarItem> items = new ArrayList<>();

		eventRepository
			.findVisibleInRange(calendar.startOf(from), calendar.startOf(to.plusDays(1)), scope.isAll(),
					visibleDepartments(scope, actor), actor.id())
			.forEach(event -> items.add(toItem(event)));

		var spec = TaskSpecifications.visibleTo(scope)
			.and(TaskSpecifications.dueBetween(from, to))
			.and(TaskSpecifications.statusIn(NOT_CANCELLED))
			.and(mine ? TaskSpecifications.assignee(actor.id()) : TaskSpecifications.all());
		taskRepository.findAll(spec, PageRequest.of(0, MAX_TASKS, Sort.by("dueDate", "id")))
			.forEach(task -> items.add(toItem(task, today)));

		if (actor.hasPermission("PROJECT_VIEW")) {
			ProjectAccess projects = new ProjectAccess(actor, scope);
			milestoneRepository.findOpenDueBetween(from, to)
				.stream()
				.filter(milestone -> projects.canView(milestone.getProject()))
				.forEach(milestone -> items.add(toItem(milestone)));
		}
		if (actor.hasPermission("APPROVAL_VIEW")) {
			approvalService.pendingDueBetween(from, to, actor).forEach(approval -> items.add(toItem(approval)));
		}

		items.sort(Comparator.comparing(CalendarItem::startDate)
			.thenComparing(item -> item.kind() == ItemKind.TASK_DEADLINE)
			.thenComparing(item -> item.startAt() == null ? Instant.MIN : item.startAt())
			.thenComparing(CalendarItem::key));
		return new CalendarResponse(today, from, to, items);
	}

	@Transactional(readOnly = true)
	public EventDetail get(Long id, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		return toDetail(loadVisible(id, scope, actor), scope, actor);
	}

	@Transactional
	public EventDetail create(SaveEvent request, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		CalendarEvent event = new CalendarEvent();
		event.setCreatedBy(userRepository.getReferenceById(actor.id()));
		apply(event, request, scope, actor);
		return toDetail(eventRepository.save(event), scope, actor);
	}

	@Transactional
	public EventDetail update(Long id, SaveEvent request, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		CalendarEvent event = loadVisible(id, scope, actor);
		requireEdit(event, scope, actor);
		if (!Objects.equals(event.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this event just now. Reload and try again.");
		}
		apply(event, request, scope, actor);
		eventRepository.flush();
		return toDetail(event, scope, actor);
	}

	@Transactional
	public void delete(Long id, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		CalendarEvent event = loadVisible(id, scope, actor);
		requireEdit(event, scope, actor);
		eventRepository.delete(event);
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private void apply(CalendarEvent event, SaveEvent request, AccessScope scope, AuthenticatedUser actor) {
		User person = null;
		Department department = null;
		if (request.userId() != null) {
			person = userRepository.findWithDepartmentById(request.userId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "User not found"));
			department = person.getDepartment();
		}
		else if (request.departmentId() != null) {
			department = departmentRepository.findById(request.departmentId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		}
		if (request.eventType() == CalendarEventType.LEAVE && person == null) {
			throw ApiException.badRequest("LEAVE_NEEDS_USER", "Leave must name the person who is away");
		}
		if (!canManage(department, person, scope, actor)) {
			throw ApiException.forbidden("FORBIDDEN", department == null
					? "Only a Super Admin can add company-wide events" : "You cannot add events for this team or person");
		}

		event.setTitle(request.title().trim());
		event.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
		event.setEventType(request.eventType());
		event.setAllDay(request.allDay());
		event.setDepartment(department);
		event.setUser(person);
		if (request.allDay()) {
			if (request.startDate() == null || request.endDate() == null
					|| request.endDate().isBefore(request.startDate())) {
				throw ApiException.badRequest("INVALID_DATES",
						"All-day events need a start and end date, with the end on or after the start");
			}
			event.setStartAt(calendar.startOf(request.startDate()));
			event.setEndAt(calendar.startOf(request.endDate().plusDays(1)));
		}
		else {
			if (request.startAt() == null || request.endAt() == null || !request.endAt().isAfter(request.startAt())) {
				throw ApiException.badRequest("INVALID_DATES", "Timed events need a start and an end after the start");
			}
			event.setStartAt(request.startAt());
			event.setEndAt(request.endAt());
		}
	}

	private CalendarEvent loadVisible(Long id, AccessScope scope, AuthenticatedUser actor) {
		return eventRepository.findDetailedById(id)
			.filter(event -> canView(event, scope, actor))
			.orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "Event not found"));
	}

	static boolean canView(CalendarEvent event, AccessScope scope, AuthenticatedUser actor) {
		if (scope.isAll() || event.getDepartment() == null || event.isCreatedBy(actor.id())
				|| (event.getUser() != null && event.getUser().getId().equals(actor.id()))) {
			return true;
		}
		Long departmentId = event.getDepartment().getId();
		return departmentId.equals(actor.departmentId()) || scope.coversDepartment(departmentId);
	}

	private boolean canEdit(CalendarEvent event, AccessScope scope, AuthenticatedUser actor) {
		return actor.hasPermission("CALENDAR_EDIT")
				&& (event.isCreatedBy(actor.id()) || canManage(event.getDepartment(), event.getUser(), scope, actor));
	}

	private void requireEdit(CalendarEvent event, AccessScope scope, AuthenticatedUser actor) {
		if (!canEdit(event, scope, actor)) {
			throw ApiException.forbidden("FORBIDDEN", "You do not have permission to change this event");
		}
	}

	private boolean canManage(Department department, User person, AccessScope scope, AuthenticatedUser actor) {
		if (scope.isAll()) {
			return true;
		}
		if (person != null) {
			return accessScopeService.canViewWorkOf(actor, person.getId(), person.getDepartment().getId());
		}
		return department != null && accessScopeService.canManageDepartment(actor, department.getId());
	}

	private static Set<Long> visibleDepartments(AccessScope scope, AuthenticatedUser actor) {
		Set<Long> ids = new HashSet<>(scope.departmentIds());
		ids.add(actor.departmentId());
		return ids;
	}

	private CalendarItem toItem(CalendarEvent event) {
		return new CalendarItem("EVENT-" + event.getId(), ItemKind.EVENT, event.getId(), event.getTitle(),
				event.getEventType(), event.isAllDay(), startDate(event), endDate(event),
				event.isAllDay() ? null : event.getStartAt(), event.isAllDay() ? null : event.getEndAt(),
				event.getDepartment() == null ? null : DepartmentSummary.of(event.getDepartment()),
				UserSummary.of(event.getUser()), null, null, null);
	}

	private static CalendarItem toItem(Task task, LocalDate today) {
		return new CalendarItem("TASK-" + task.getId(), ItemKind.TASK_DEADLINE, task.getId(), task.getTitle(), null,
				true, task.getDueDate(), task.getDueDate(), null, null, DepartmentSummary.of(task.getDepartment()),
				UserSummary.of(task.getAssignee()), new TaskInfo(task.getCode(), task.getStatus(), task.getPriority(),
						DueState.of(task.getDueDate(), task.getStatus(), today)),
				task.getCode(), null);
	}

	private static CalendarItem toItem(ProjectMilestone milestone) {
		Project project = milestone.getProject();
		return new CalendarItem("MILESTONE-" + milestone.getId(), ItemKind.MILESTONE, milestone.getId(),
				milestone.getName(), null, true, milestone.getDueDate(), milestone.getDueDate(), null, null,
				DepartmentSummary.of(project.getDepartment()), UserSummary.of(project.getOwner()), null,
				project.getCode(), project.getId());
	}

	private static CalendarItem toItem(Approval approval) {
		return new CalendarItem("APPROVAL-" + approval.getId(), ItemKind.APPROVAL_DUE, approval.getId(),
				approval.getTitle(), null, true, approval.getDueDate(), approval.getDueDate(), null, null,
				DepartmentSummary.of(approval.getDepartment()), UserSummary.of(approval.getRequester()), null,
				approval.getCode(), null);
	}

	private EventDetail toDetail(CalendarEvent event, AccessScope scope, AuthenticatedUser actor) {
		return new EventDetail(event.getId(), event.getTitle(), event.getDescription(), event.getEventType(),
				event.isAllDay(), startDate(event), endDate(event), event.getStartAt(), event.getEndAt(),
				event.getDepartment() == null ? null : DepartmentSummary.of(event.getDepartment()),
				UserSummary.of(event.getUser()), UserSummary.of(event.getCreatedBy()), event.getVersion(),
				canEdit(event, scope, actor));
	}

	private LocalDate startDate(CalendarEvent event) {
		return LocalDate.ofInstant(event.getStartAt(), calendar.zone());
	}

	/** Inclusive last day: all-day events end at the next midnight, so step back a day. */
	private LocalDate endDate(CalendarEvent event) {
		LocalDate end = LocalDate.ofInstant(event.getEndAt(), calendar.zone());
		return event.isAllDay() ? end.minusDays(1) : end;
	}

}
