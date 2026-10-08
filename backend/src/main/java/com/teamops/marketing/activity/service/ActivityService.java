package com.teamops.marketing.activity.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.activity.dto.ActivityDtos.ActivityDetail;
import com.teamops.marketing.activity.dto.ActivityDtos.ActivityListItem;
import com.teamops.marketing.activity.dto.ActivityDtos.ActivityPermissions;
import com.teamops.marketing.activity.dto.ActivityDtos.CreateActivity;
import com.teamops.marketing.activity.dto.ActivityDtos.OccurrenceItem;
import com.teamops.marketing.activity.dto.ActivityDtos.UpdateActivity;
import com.teamops.marketing.activity.entity.ActivityChecklistItem;
import com.teamops.marketing.activity.entity.ActivityOccurrence;
import com.teamops.marketing.activity.entity.Frequency;
import com.teamops.marketing.activity.entity.MarketingActivity;
import com.teamops.marketing.activity.entity.OccurrenceStatus;
import com.teamops.marketing.activity.repository.ActivityOccurrenceRepository;
import com.teamops.marketing.activity.repository.MarketingActivityRepository;
import com.teamops.marketing.activity.service.Recurrence.Period;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.task.dto.DueState;
import com.teamops.task.dto.TaskRef;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;

/**
 * Recurring marketing activities (brief sections 35–36): MARKETING_VIEW reads, MARKETING_EDIT configures. Edits apply
 * to occurrences generated afterwards; frequency and start date are fixed once an activity has occurrences, so the
 * periods of its history stay consistent. Last completed, next due and overdue are derived from the occurrences.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

	public static final String MARKETING_EDIT = "MARKETING_EDIT";

	private static final String ENTITY = "MARKETING_ACTIVITY";

	private final MarketingActivityRepository activityRepository;

	private final ActivityOccurrenceRepository occurrenceRepository;

	private final OccurrenceEngine engine;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	// --- reading ----------------------------------------------------------------------------------------------

	public PageResponse<ActivityListItem> search(String search, Frequency frequency, Boolean active, Long ownerId,
			Pageable pageable, AuthenticatedUser viewer) {
		Page<MarketingActivity> page = activityRepository.findAll(activitySpec(search, frequency, active, ownerId),
				pageable);
		List<Long> ids = page.getContent().stream().map(MarketingActivity::getId).toList();
		Map<Long, Instant> lastCompleted = lastCompleted(ids);
		Map<Long, List<ActivityOccurrence>> open = new HashMap<>();
		if (!ids.isEmpty()) {
			occurrenceRepository.findOpen(ids, OccurrenceEngine.OPEN)
				.forEach(o -> open.computeIfAbsent(o.getActivity().getId(), id -> new ArrayList<>()).add(o));
		}
		LocalDate today = calendar.today();
		return PageResponse.of(page.map(activity -> {
			List<ActivityOccurrence> pending = open.getOrDefault(activity.getId(), List.of());
			int overdue = (int) pending.stream().filter(o -> o.getDueDate().isBefore(today)).count();
			return new ActivityListItem(activity.getId(), activity.getName(), activity.getFrequency(),
					DepartmentSummary.of(activity.getDepartment()), UserSummary.of(activity.getOwner()),
					UserSummary.of(activity.assignee()), activity.getStartDate(), activity.getEndDate(),
					activity.isActive(), activity.generatesTasks(), lastCompleted.get(activity.getId()),
					pending.isEmpty() ? null : toItem(pending.getFirst(), today, viewer), pending.size(), overdue,
					activity.getUpdatedAt());
		}));
	}

	public ActivityDetail get(Long id, AuthenticatedUser viewer) {
		return toDetail(load(id), viewer);
	}

	/** One activity's occurrences, newest period first. */
	public PageResponse<OccurrenceItem> occurrencesOf(Long activityId, Pageable pageable, AuthenticatedUser viewer) {
		load(activityId);
		return occurrences(null, null, null, null, activityId, pageable, viewer);
	}

	/** Occurrences across activities, e.g. what is due this month or overdue. */
	public PageResponse<OccurrenceItem> occurrences(Set<OccurrenceStatus> statuses, LocalDate dueFrom,
			LocalDate dueTo, Long assigneeId, Long activityId, Pageable pageable, AuthenticatedUser viewer) {
		Specification<ActivityOccurrence> spec = (root, query, cb) -> cb.conjunction();
		if (statuses != null && !statuses.isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("status").in(statuses));
		}
		if (dueFrom != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), dueFrom));
		}
		if (dueTo != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), dueTo));
		}
		if (activityId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("activity").get("id"), activityId));
		}
		if (assigneeId != null) {
			// The task's assignee, else the activity's default assignee, else its owner.
			spec = spec.and((root, query, cb) -> {
				var activity = root.join("activity");
				var task = root.join("task", JoinType.LEFT);
				return cb.or(cb.equal(task.get("assignee").get("id"), assigneeId),
						cb.and(cb.isNull(task.get("id")), cb.or(cb.equal(activity.get("defaultAssignee").get("id"), assigneeId),
								cb.and(cb.isNull(activity.get("defaultAssignee")), cb.equal(activity.get("owner").get("id"), assigneeId)))));
			});
		}
		LocalDate today = calendar.today();
		return PageResponse.of(occurrenceRepository.findAll(spec, pageable).map(o -> toItem(o, today, viewer)));
	}

	// --- configuration ------------------------------------------------------------------------------------------

	@Transactional
	public ActivityDetail create(CreateActivity request, AuthenticatedUser actor, ClientInfo client) {
		validateDates(request.startDate(), request.endDate());
		MarketingActivity activity = new MarketingActivity();
		activity.setName(request.name().trim());
		activity.setDescription(trimToNull(request.description()));
		activity.setDepartment(resolveDepartment(request.departmentId()));
		activity.setOwner(resolveMarketingUser(request.ownerId(), "owner"));
		activity.setFrequency(request.frequency());
		activity.setStartDate(request.startDate());
		activity.setEndDate(request.endDate());
		activity.setDueOffsetDays(request.dueOffsetDays());
		activity.setTaskTitleTemplate(trimToNull(request.taskTitleTemplate()));
		activity.setDefaultAssignee(resolveMarketingUser(request.defaultAssigneeId(), "assignee"));
		activity.setTaskPriority(request.taskPriority() == null ? TaskPriority.MEDIUM : request.taskPriority());
		replaceChecklist(activity, request.checklist());
		MarketingActivity saved = activityRepository.save(activity);
		auditService.record(AuditAction.MARKETING_ACTIVITY_CREATED, actor.id(), ENTITY, saved.getId(),
				Map.of("name", saved.getName(), "frequency", saved.getFrequency()), client);
		// The current period starts straight away; later periods follow completions and the daily job.
		engine.ensureCurrent(saved, calendar.today());
		return toDetail(saved, actor);
	}

	@Transactional
	public ActivityDetail update(Long id, UpdateActivity request, AuthenticatedUser actor, ClientInfo client) {
		MarketingActivity activity = load(id);
		if (!Objects.equals(activity.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this activity just now. Reload and try again.");
		}
		validateDates(request.startDate(), request.endDate());
		boolean inUse = occurrenceRepository.existsByActivityId(id);
		if (inUse && (activity.getFrequency() != request.frequency() || !activity.getStartDate().equals(request.startDate()))) {
			throw ApiException.conflict("ACTIVITY_IN_USE",
					"This activity already has occurrences, so its frequency and start date can no longer change");
		}
		Department department = activity.getDepartment().getId().equals(request.departmentId()) ? activity.getDepartment()
				: resolveDepartment(request.departmentId());
		User owner = Objects.equals(userId(activity.getOwner()), request.ownerId()) ? activity.getOwner()
				: resolveMarketingUser(request.ownerId(), "owner");
		User assignee = Objects.equals(userId(activity.getDefaultAssignee()), request.defaultAssigneeId())
				? activity.getDefaultAssignee() : resolveMarketingUser(request.defaultAssigneeId(), "assignee");
		TaskPriority priority = request.taskPriority() == null ? TaskPriority.MEDIUM : request.taskPriority();
		String template = trimToNull(request.taskTitleTemplate());
		List<String> checklist = cleanChecklist(request.checklist());
		AuditChanges changes = new AuditChanges().track("name", activity.getName(), request.name().trim())
			.track("frequency", activity.getFrequency(), request.frequency())
			.track("startDate", activity.getStartDate(), request.startDate())
			.track("endDate", activity.getEndDate(), request.endDate())
			.track("dueOffsetDays", activity.getDueOffsetDays(), request.dueOffsetDays())
			.track("taskTitleTemplate", activity.getTaskTitleTemplate(), template)
			.track("ownerId", userId(activity.getOwner()), userId(owner))
			.track("defaultAssigneeId", userId(activity.getDefaultAssignee()), userId(assignee))
			.track("departmentId", activity.getDepartment().getId(), department.getId())
			.track("taskPriority", activity.getTaskPriority(), priority)
			.track("checklist", checklistOf(activity), checklist)
			.track("active", activity.isActive(), request.active());
		activity.setName(request.name().trim());
		activity.setDescription(trimToNull(request.description()));
		activity.setDepartment(department);
		activity.setOwner(owner);
		activity.setFrequency(request.frequency());
		activity.setStartDate(request.startDate());
		activity.setEndDate(request.endDate());
		activity.setDueOffsetDays(request.dueOffsetDays());
		activity.setTaskTitleTemplate(template);
		activity.setDefaultAssignee(assignee);
		activity.setTaskPriority(priority);
		activity.setActive(request.active());
		if (!checklist.equals(checklistOf(activity))) {
			replaceChecklist(activity, checklist);
		}
		activityRepository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.MARKETING_ACTIVITY_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), client);
		}
		engine.ensureCurrent(activity, calendar.today());
		return toDetail(activity, actor);
	}

	/** Only an activity without occurrences can be deleted; one with history is deactivated instead. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		MarketingActivity activity = load(id);
		if (occurrenceRepository.existsByActivityId(id)) {
			throw ApiException.conflict("ACTIVITY_IN_USE",
					"This activity has occurrences. Deactivate it instead, so its history is kept.");
		}
		activityRepository.delete(activity);
		auditService.record(AuditAction.MARKETING_ACTIVITY_DELETED, actor.id(), ENTITY, id,
				Map.of("name", activity.getName()), client);
	}

	// --- occurrences without a task ------------------------------------------------------------------------------

	/** Completes an occurrence that has no task, which creates the next one. */
	@Transactional
	public OccurrenceItem complete(Long occurrenceId, String notes, AuthenticatedUser actor, ClientInfo client) {
		return act(occurrenceId, OccurrenceStatus.COMPLETED, notes, actor, client);
	}

	/** Skips an occurrence that has no task (e.g. not needed this period), which creates the next one. */
	@Transactional
	public OccurrenceItem skip(Long occurrenceId, String notes, AuthenticatedUser actor, ClientInfo client) {
		return act(occurrenceId, OccurrenceStatus.SKIPPED, notes, actor, client);
	}

	/** Reopens a closed occurrence that has no task; the next occurrence is kept. */
	@Transactional
	public OccurrenceItem reopen(Long occurrenceId, AuthenticatedUser actor, ClientInfo client) {
		ActivityOccurrence occurrence = actionable(occurrenceId, actor);
		if (occurrence.getStatus().isOpen()) {
			throw ApiException.conflict("OCCURRENCE_OPEN", "This occurrence is still open");
		}
		OccurrenceStatus previous = occurrence.getStatus();
		engine.reopen(occurrence, OccurrenceStatus.PENDING);
		occurrenceRepository.flush();
		auditOccurrence(AuditAction.MARKETING_ACTIVITY_OCCURRENCE_REOPENED, occurrence, previous, actor, client);
		return toItem(occurrence, calendar.today(), actor);
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private OccurrenceItem act(Long occurrenceId, OccurrenceStatus status, String notes, AuthenticatedUser actor,
			ClientInfo client) {
		ActivityOccurrence occurrence = actionable(occurrenceId, actor);
		if (!occurrence.getStatus().isOpen()) {
			throw ApiException.conflict("OCCURRENCE_CLOSED", "This occurrence is already " + occurrence.getStatus()
				.name()
				.toLowerCase(Locale.ROOT) + ". Reopen it first.");
		}
		OccurrenceStatus previous = occurrence.getStatus();
		if (StringUtils.hasText(notes)) {
			occurrence.setNotes(notes.trim());
		}
		engine.close(occurrence, status, actor.id());
		auditOccurrence(status == OccurrenceStatus.COMPLETED ? AuditAction.MARKETING_ACTIVITY_OCCURRENCE_COMPLETED
				: AuditAction.MARKETING_ACTIVITY_OCCURRENCE_SKIPPED, occurrence, previous, actor, client);
		return toItem(occurrence, calendar.today(), actor);
	}

	/** Occurrences with a task follow the task; only the activity's people or marketing managers act on the rest. */
	private ActivityOccurrence actionable(Long occurrenceId, AuthenticatedUser actor) {
		ActivityOccurrence occurrence = occurrenceRepository.findDetailedById(occurrenceId)
			.orElseThrow(() -> ApiException.notFound("OCCURRENCE_NOT_FOUND", "Occurrence not found"));
		if (occurrence.getTask() != null) {
			throw ApiException.conflict("OCCURRENCE_HAS_TASK", "This occurrence follows its task "
					+ occurrence.getTask().getCode() + ". Complete or reopen the task instead.");
		}
		if (!canAct(occurrence.getActivity(), actor)) {
			throw ApiException.forbidden("FORBIDDEN", "Only the activity's owner, assignee or a marketing manager can do this");
		}
		return occurrence;
	}

	private static boolean canAct(MarketingActivity activity, AuthenticatedUser actor) {
		return actor.hasPermission(MARKETING_EDIT) || actor.id().equals(userId(activity.getOwner()))
				|| actor.id().equals(userId(activity.assignee()));
	}

	private void auditOccurrence(AuditAction action, ActivityOccurrence occurrence, OccurrenceStatus previous,
			AuthenticatedUser actor, ClientInfo client) {
		Map<String, Object> details = new HashMap<>();
		details.put("activityId", occurrence.getActivity().getId());
		details.put("periodStart", occurrence.getPeriodStart().toString());
		details.put("from", previous);
		details.put("to", occurrence.getStatus());
		auditService.record(action, actor.id(), "MARKETING_ACTIVITY_OCCURRENCE", occurrence.getId(), details, client);
	}

	private ActivityDetail toDetail(MarketingActivity activity, AuthenticatedUser viewer) {
		LocalDate today = calendar.today();
		List<ActivityOccurrence> open = occurrenceRepository.findOpen(List.of(activity.getId()), OccurrenceEngine.OPEN);
		List<ActivityOccurrence> recent = occurrenceRepository.findTop12ByActivityIdOrderByPeriodStartDesc(activity.getId());
		long count = occurrenceRepository.countByActivityId(activity.getId());
		// The next period to be generated: after the latest occurrence, or the current one (from the start date).
		Period next = recent.isEmpty()
				? Recurrence.containing(activity.getFrequency(), today.isBefore(activity.getStartDate()) ? activity.getStartDate() : today)
				: Recurrence.next(activity.getFrequency(), new Period(recent.getFirst().getPeriodStart(), recent.getFirst().getPeriodEnd()));
		return new ActivityDetail(activity.getId(), activity.getName(), activity.getDescription(), activity.getFrequency(),
				DepartmentSummary.of(activity.getDepartment()), UserSummary.of(activity.getOwner()),
				UserSummary.of(activity.getDefaultAssignee()), activity.getStartDate(), activity.getEndDate(),
				activity.getDueOffsetDays(), activity.getTaskTitleTemplate(),
				activity.generatesTasks() ? Recurrence.title(activity.getTaskTitleTemplate(), activity.getFrequency(), next) : null,
				activity.getTaskPriority(), activity.isActive(), checklistOf(activity),
				lastCompleted(List.of(activity.getId())).get(activity.getId()),
				open.isEmpty() ? null : toItem(open.getFirst(), today, viewer),
				recent.stream().map(o -> toItem(o, today, viewer)).toList(), count, count > 0, activity.getVersion(),
				activity.getCreatedAt(), activity.getUpdatedAt(), new ActivityPermissions(viewer.hasPermission(MARKETING_EDIT)));
	}

	private OccurrenceItem toItem(ActivityOccurrence occurrence, LocalDate today, AuthenticatedUser viewer) {
		MarketingActivity activity = occurrence.getActivity();
		// Open occurrences count as active work for the due state; closed ones have none.
		DueState dueState = DueState.of(occurrence.getDueDate(),
				occurrence.getStatus().isOpen() ? TaskStatus.TODO : TaskStatus.COMPLETED, today);
		User assignee = occurrence.getTask() != null ? occurrence.getTask().getAssignee() : activity.assignee();
		return new OccurrenceItem(occurrence.getId(), activity.getId(), activity.getName(), activity.getFrequency(),
				occurrence.getPeriodStart(), occurrence.getPeriodEnd(),
				Recurrence.label(activity.getFrequency(), new Period(occurrence.getPeriodStart(), occurrence.getPeriodEnd())),
				occurrence.getDueDate(), dueState, occurrence.getStatus(),
				occurrence.getTask() == null ? null : TaskRef.of(occurrence.getTask()), UserSummary.of(assignee),
				occurrence.getCompletedAt(), UserSummary.of(occurrence.getCompletedBy()), occurrence.getNotes(),
				occurrence.getVersion(), occurrence.getTask() == null && canAct(activity, viewer));
	}

	private Map<Long, Instant> lastCompleted(List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return occurrenceRepository.findLastCompleted(ids)
			.stream()
			.filter(row -> row.getCompletedAt() != null)
			.collect(Collectors.toMap(ActivityOccurrenceRepository.LastCompleted::getActivityId,
					ActivityOccurrenceRepository.LastCompleted::getCompletedAt));
	}

	private static Specification<MarketingActivity> activitySpec(String search, Frequency frequency, Boolean active,
			Long ownerId) {
		Specification<MarketingActivity> spec = (root, query, cb) -> cb.conjunction();
		if (StringUtils.hasText(search)) {
			String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
				.replace("_", "\\_") + "%";
			spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
		}
		if (frequency != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("frequency"), frequency));
		}
		if (active != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
		}
		if (ownerId != null) {
			spec = spec.and((root, query, cb) -> cb.or(cb.equal(root.get("owner").get("id"), ownerId),
					cb.equal(root.get("defaultAssignee").get("id"), ownerId)));
		}
		return spec;
	}

	private static void replaceChecklist(MarketingActivity activity, List<String> items) {
		activity.getChecklist().clear();
		int position = 0;
		for (String content : cleanChecklist(items)) {
			ActivityChecklistItem item = new ActivityChecklistItem();
			item.setActivity(activity);
			item.setContent(content);
			item.setPosition(position++);
			activity.getChecklist().add(item);
		}
	}

	private static List<String> cleanChecklist(List<String> items) {
		return items == null ? List.of() : items.stream().filter(StringUtils::hasText).map(String::trim).toList();
	}

	private static List<String> checklistOf(MarketingActivity activity) {
		return activity.getChecklist().stream().map(ActivityChecklistItem::getContent).toList();
	}

	private MarketingActivity load(Long id) {
		return activityRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("ACTIVITY_NOT_FOUND", "Activity not found"));
	}

	private Department resolveDepartment(Long departmentId) {
		Department department = departmentRepository.findById(departmentId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		if (department.getStatus() != DepartmentStatus.ACTIVE) {
			throw ApiException.badRequest("DEPARTMENT_INACTIVE", "Choose an active department");
		}
		return department;
	}

	/** Owners and assignees must be active Digital Marketing users, so they can see the activity and its tasks. */
	private User resolveMarketingUser(Long userId, String role) {
		if (userId == null) {
			return null;
		}
		return userRepository.findActiveWithPermission(MarketingContextService.MARKETING_VIEW, UserStatus.ACTIVE)
			.stream()
			.filter(user -> user.getId().equals(userId))
			.findFirst()
			.orElseThrow(() -> ApiException.badRequest("INVALID_" + role.toUpperCase(Locale.ROOT),
					"The " + role + " must be an active user with access to Digital Marketing"));
	}

	private static void validateDates(LocalDate start, LocalDate end) {
		if (end != null && end.isBefore(start)) {
			throw ApiException.badRequest("INVALID_DATES", "The end date cannot be before the start date");
		}
	}

	private static Long userId(User user) {
		return user == null ? null : user.getId();
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
