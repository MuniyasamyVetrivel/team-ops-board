package com.teamops.task.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.project.dto.ProjectRef;
import com.teamops.project.entity.Project;
import com.teamops.project.repository.ProjectRepository;
import com.teamops.tag.Tag;
import com.teamops.tag.TagService;
import com.teamops.task.dto.DueState;
import com.teamops.task.dto.MyTaskSummary;
import com.teamops.task.dto.TaskChildDtos.AttachmentResponse;
import com.teamops.task.dto.TaskChildDtos.ChecklistItemResponse;
import com.teamops.task.dto.TaskChildDtos.CommentResponse;
import com.teamops.task.dto.TaskChildDtos.HistoryEntry;
import com.teamops.task.dto.TaskDetail;
import com.teamops.task.dto.TaskListItem;
import com.teamops.task.dto.TaskRef;
import com.teamops.task.dto.TaskRequests;
import com.teamops.task.dto.TaskSearchCriteria;
import com.teamops.task.entity.Task;
import com.teamops.task.event.TaskAssignedEvent;
import com.teamops.task.event.TaskStatusChangedEvent;
import com.teamops.task.entity.TaskHistory;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.repository.TaskAttachmentRepository;
import com.teamops.task.repository.TaskChecklistItemRepository;
import com.teamops.task.repository.TaskCommentRepository;
import com.teamops.task.repository.TaskHistoryRepository;
import com.teamops.task.repository.TaskRepository;
import com.teamops.task.repository.TaskSpecifications;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Core task operations. Every change is checked with {@link TaskAccess}, written to task_history, and creation,
 * assignment and status changes are also audited (brief section 63). Tasks the actor may not see are reported as
 * "not found" so their existence is not revealed.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

	private final TaskRepository taskRepository;

	private final TaskCommentRepository commentRepository;

	private final TaskChecklistItemRepository checklistRepository;

	private final TaskHistoryRepository historyRepository;

	private final TaskAttachmentRepository attachmentRepository;

	private final DepartmentRepository departmentRepository;

	private final ProjectRepository projectRepository;

	private final UserRepository userRepository;

	private final TagService tagService;

	private final CodeGenerator codeGenerator;

	private final AccessScopeService accessScopeService;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	private final ApplicationEventPublisher events;

	// --- queries --------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<TaskListItem> search(TaskSearchCriteria criteria, Pageable pageable, AuthenticatedUser actor) {
		LocalDate today = calendar.today();
		var spec = TaskSpecifications.visibleTo(accessScopeService.scopeFor(actor))
			.and(TaskSpecifications.view(criteria.view(), actor.id()))
			.and(TaskSpecifications.matches(criteria.search()))
			.and(TaskSpecifications.statusIn(criteria.statuses()))
			.and(TaskSpecifications.priorityIn(criteria.priorities()))
			.and(TaskSpecifications.assignee(criteria.assigneeId()))
			.and(TaskSpecifications.department(criteria.departmentId()))
			.and(TaskSpecifications.project(criteria.projectId()))
			.and(TaskSpecifications.due(criteria.due(), today));
		return PageResponse.of(taskRepository.findAll(spec, pageable).map(task -> TaskListItem.of(task, today)));
	}

	@Transactional(readOnly = true)
	public MyTaskSummary mySummary(AuthenticatedUser actor) {
		LocalDate today = calendar.today();
		return MyTaskSummary.of(taskRepository.countForAssignee(actor.id(), TaskStatus.ACTIVE, today,
				today.plusDays(7), calendar.startOf(calendar.startOfWeek())), today);
	}

	@Transactional(readOnly = true)
	public TaskDetail get(Long id, AuthenticatedUser actor) {
		TaskAccess access = access(actor);
		return toDetail(loadVisible(id, access), access);
	}

	// --- commands -------------------------------------------------------------------------------------------

	@Transactional
	public TaskDetail create(TaskRequests.CreateTask request, AuthenticatedUser actor, ClientInfo client) {
		TaskAccess access = access(actor);
		Long departmentId = request.departmentId() != null ? request.departmentId() : actor.departmentId();
		if (!access.canCreateIn(departmentId)) {
			throw ApiException.forbidden("FORBIDDEN", "You can only create tasks in departments you belong to or manage");
		}
		User assignee = resolveAssignee(request.assigneeId());
		if (!access.canAssignOnCreate(assignee)) {
			throw ApiException.forbidden("CANNOT_ASSIGN", "You can only assign tasks to people within your scope");
		}
		validateDates(request.startDate(), request.dueDate());

		Task task = new Task();
		task.setCode(codeGenerator.next(CodeGenerator.TASK));
		task.setTitle(request.title().trim());
		task.setDescription(trimToNull(request.description()));
		task.setDepartment(resolveActiveDepartment(departmentId));
		task.setProject(resolveProject(request.projectId()));
		task.setAssignee(assignee);
		task.setCreatedBy(userRepository.getReferenceById(actor.id()));
		task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
		task.setStartDate(request.startDate());
		task.setDueDate(request.dueDate());
		task.setEstimatedHours(request.estimatedHours());
		task.setTags(new java.util.HashSet<>(tagService.resolve(request.tags())));
		Task saved = taskRepository.save(task);

		history(saved, actor, "created", null, saved.getCode());
		auditService.record(AuditAction.TASK_CREATED, actor.id(), "TASK", saved.getId(),
				Map.of("code", saved.getCode(), "departmentId", departmentId, "assigneeId",
						assignee == null ? "none" : assignee.getId()),
				client);
		publishAssigned(saved, actor);
		return toDetail(saved, access);
	}

	@Transactional
	public TaskDetail update(Long id, TaskRequests.UpdateTask request, AuthenticatedUser actor) {
		TaskAccess access = access(actor);
		Task task = loadVisible(id, access);
		requireEdit(access, task);
		if (!Objects.equals(task.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this task just now. Reload and try again.");
		}
		validateDates(request.startDate(), request.dueDate());

		if (!task.getDepartment().getId().equals(request.departmentId())) {
			if (!access.canCreateIn(request.departmentId())) {
				throw ApiException.forbidden("FORBIDDEN", "You cannot move tasks into that department");
			}
			Department department = resolveActiveDepartment(request.departmentId());
			history(task, actor, "department", task.getDepartment().getName(), department.getName());
			task.setDepartment(department);
		}
		Project project = resolveProject(request.projectId());
		if (!Objects.equals(projectId(task.getProject()), projectId(project))) {
			history(task, actor, "project", projectName(task.getProject()), projectName(project));
			task.setProject(project);
		}
		String title = request.title().trim();
		track(task, actor, "title", task.getTitle(), title);
		task.setTitle(title);
		String description = trimToNull(request.description());
		if (!Objects.equals(task.getDescription(), description)) {
			history(task, actor, "description", null, null);
			task.setDescription(description);
		}
		track(task, actor, "priority", task.getPriority(), request.priority());
		task.setPriority(request.priority());
		track(task, actor, "startDate", task.getStartDate(), request.startDate());
		task.setStartDate(request.startDate());
		track(task, actor, "dueDate", task.getDueDate(), request.dueDate());
		task.setDueDate(request.dueDate());
		track(task, actor, "estimatedHours", plain(task.getEstimatedHours()), plain(request.estimatedHours()));
		task.setEstimatedHours(request.estimatedHours());
		track(task, actor, "actualHours", plain(task.getActualHours()), plain(request.actualHours()));
		task.setActualHours(request.actualHours());

		Set<Tag> tags = tagService.resolve(request.tags());
		String oldTags = tagNames(task.getTags());
		String newTags = tagNames(tags);
		if (!oldTags.equals(newTags)) {
			history(task, actor, "tags", oldTags, newTags);
			task.setTags(new java.util.HashSet<>(tags));
		}
		taskRepository.flush();
		return toDetail(task, access);
	}

	@Transactional
	public TaskDetail assign(Long id, Long assigneeId, AuthenticatedUser actor, ClientInfo client) {
		TaskAccess access = access(actor);
		Task task = loadVisible(id, access);
		requireEdit(access, task);
		User assignee = resolveAssignee(assigneeId);
		if (!access.canAssign(task, assignee)) {
			throw ApiException.forbidden("CANNOT_ASSIGN", "You can only assign tasks to people within your scope");
		}
		if (Objects.equals(userId(task.getAssignee()), assigneeId)) {
			return toDetail(task, access);
		}
		Long previous = userId(task.getAssignee());
		history(task, actor, "assignee", nameOf(task.getAssignee()), nameOf(assignee));
		task.setAssignee(assignee);
		auditService.record(AuditAction.TASK_ASSIGNED, actor.id(), "TASK", task.getId(),
				Map.of("code", task.getCode(), "from", previous == null ? "none" : previous, "to",
						assigneeId == null ? "none" : assigneeId),
				client);
		publishAssigned(task, actor);
		taskRepository.flush();
		return toDetail(task, access);
	}

	/**
	 * Moves the task to a new status. COMPLETED stamps {@code completedAt}; moving a closed task back to an active
	 * status is a reopen and clears it. Cancelling needs TASK_DELETE.
	 */
	@Transactional
	public TaskDetail changeStatus(Long id, TaskStatus status, AuthenticatedUser actor, ClientInfo client) {
		TaskAccess access = access(actor);
		Task task = loadVisible(id, access);
		requireEdit(access, task);
		TaskStatus previous = task.getStatus();
		if (previous == status) {
			return toDetail(task, access);
		}
		if (status == TaskStatus.CANCELLED && !access.canCancel(task)) {
			throw ApiException.forbidden("CANNOT_CANCEL", "You do not have permission to cancel tasks");
		}
		task.setStatus(status);
		if (status == TaskStatus.COMPLETED) {
			task.setCompletedAt(calendar.now());
		}
		else if (previous == TaskStatus.COMPLETED) {
			task.setCompletedAt(null);
		}
		boolean reopened = previous.isClosed() && status.isActive();
		history(task, actor, reopened ? "reopened" : "status", previous.name(), status.name());
		auditService.record(AuditAction.TASK_STATUS_CHANGED, actor.id(), "TASK", task.getId(),
				Map.of("code", task.getCode(), "from", previous, "to", status, "reopened", reopened), client);
		taskRepository.flush();
		events.publishEvent(new TaskStatusChangedEvent(task.getId(), previous, status, actor.id()));
		return toDetail(task, access);
	}

	// --- shared helpers (also used by TaskCollaborationService) -------------------------------------------

	TaskAccess access(AuthenticatedUser actor) {
		return new TaskAccess(actor, accessScopeService.scopeFor(actor));
	}

	Task loadVisible(Long id, TaskAccess access) {
		return taskRepository.findDetailedById(id)
			.filter(access::canView)
			.orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Task not found"));
	}

	static void requireEdit(TaskAccess access, Task task) {
		if (!access.canEdit(task)) {
			throw ApiException.forbidden("FORBIDDEN", "You do not have permission to change this task");
		}
	}

	void history(Task task, AuthenticatedUser actor, String field, Object oldValue, Object newValue) {
		TaskHistory entry = new TaskHistory();
		entry.setTask(task);
		entry.setChangedBy(actor == null ? null : userRepository.getReferenceById(actor.id()));
		entry.setFieldName(field);
		entry.setOldValue(display(oldValue));
		entry.setNewValue(display(newValue));
		historyRepository.save(entry);
	}

	TaskDetail toDetail(Task task, TaskAccess access) {
		Long id = task.getId();
		return new TaskDetail(id, task.getCode(), task.getTitle(), task.getDescription(), task.getStatus(),
				task.getPriority(), DepartmentSummary.of(task.getDepartment()), ProjectRef.of(task.getProject()),
				UserSummary.of(task.getAssignee()), UserSummary.of(task.getCreatedBy()), task.getStartDate(),
				task.getDueDate(), DueState.of(task.getDueDate(), task.getStatus(), calendar.today()),
				task.getEstimatedHours(), task.getActualHours(), task.getCompletedAt(), task.getSource(),
				task.getCreatedAt(), task.getUpdatedAt(), task.getVersion(),
				task.getTags().stream().map(Tag::getName).sorted().toList(),
				task.getWatchers()
					.stream()
					.sorted(Comparator.comparing(User::getFullName))
					.map(UserSummary::of)
					.toList(),
				task.getDependsOn().stream().sorted(Comparator.comparing(Task::getCode)).map(TaskRef::of).toList(),
				checklistRepository.findByTaskIdOrderByPositionAscIdAsc(id)
					.stream()
					.map(ChecklistItemResponse::of)
					.toList(),
				commentRepository.findByTaskIdOrderByCreatedAtAsc(id).stream().map(CommentResponse::of).toList(),
				attachmentRepository.findByTaskIdOrderByAddedAtAsc(id).stream().map(AttachmentResponse::of).toList(),
				historyRepository.findTop100ByTaskIdOrderByChangedAtDescIdDesc(id)
					.stream()
					.map(HistoryEntry::of)
					.toList(),
				access.permissions(task));
	}

	// --- private helpers ------------------------------------------------------------------------------------

	/** Lets listeners (notifications) react in this transaction; self-assignment notifies nobody. */
	private void publishAssigned(Task task, AuthenticatedUser actor) {
		Long assigneeId = userId(task.getAssignee());
		if (assigneeId != null && !assigneeId.equals(actor.id())) {
			events.publishEvent(new TaskAssignedEvent(task.getId(), task.getCode(), task.getTitle(), assigneeId,
					actor.id(), actor.fullName()));
		}
	}

	private void track(Task task, AuthenticatedUser actor, String field, Object oldValue, Object newValue) {
		if (!Objects.equals(oldValue, newValue)) {
			history(task, actor, field, oldValue, newValue);
		}
	}

	private User resolveAssignee(Long assigneeId) {
		if (assigneeId == null) {
			return null;
		}
		User user = userRepository.findWithDepartmentById(assigneeId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "Assignee not found"));
		if (!user.isActive()) {
			throw ApiException.badRequest("USER_DISABLED", "Tasks cannot be assigned to a disabled user");
		}
		return user;
	}

	private Department resolveActiveDepartment(Long departmentId) {
		Department department = departmentRepository.findById(departmentId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		if (department.getStatus() != DepartmentStatus.ACTIVE) {
			throw ApiException.badRequest("DEPARTMENT_INACTIVE", "Tasks cannot be added to an inactive department");
		}
		return department;
	}

	private Project resolveProject(Long projectId) {
		if (projectId == null) {
			return null;
		}
		return projectRepository.findById(projectId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_PROJECT", "Project not found"));
	}

	private static void validateDates(LocalDate start, LocalDate due) {
		if (start != null && due != null && due.isBefore(start)) {
			throw ApiException.badRequest("INVALID_DATES", "The due date cannot be before the start date");
		}
	}

	private static String display(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString();
		return text.length() > TaskHistory.MAX_VALUE_LENGTH ? text.substring(0, TaskHistory.MAX_VALUE_LENGTH) : text;
	}

	private static String tagNames(Set<Tag> tags) {
		return tags.stream().map(Tag::getName).sorted().collect(Collectors.joining(", "));
	}

	private static String plain(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}

	private static String nameOf(User user) {
		return user == null ? null : user.getFullName();
	}

	private static String projectName(Project project) {
		return project == null ? null : project.getCode() + " " + project.getName();
	}

	private static Long userId(User user) {
		return user == null ? null : user.getId();
	}

	private static Long projectId(Project project) {
		return project == null ? null : project.getId();
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
