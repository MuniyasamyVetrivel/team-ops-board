package com.teamops.ticket.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.service.NotificationService;
import com.teamops.sla.entity.SlaPolicy;
import com.teamops.sla.repository.SlaPolicyRepository;
import com.teamops.ticket.dto.TicketDtos.AttachmentResponse;
import com.teamops.ticket.dto.TicketDtos.CategoryRef;
import com.teamops.ticket.dto.TicketDtos.CategoryResponse;
import com.teamops.ticket.dto.TicketDtos.CommentResponse;
import com.teamops.ticket.dto.TicketDtos.HistoryEntry;
import com.teamops.ticket.dto.TicketDtos.MyTicketSummary;
import com.teamops.ticket.dto.TicketDtos.TicketDetail;
import com.teamops.ticket.dto.TicketDtos.TicketListItem;
import com.teamops.ticket.dto.TicketDtos.TicketPermissions;
import com.teamops.ticket.dto.TicketDtos.TicketSla;
import com.teamops.ticket.dto.TicketRequests;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketCategory;
import com.teamops.ticket.entity.TicketHistory;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.repository.MyTicketCounts;
import com.teamops.ticket.repository.TicketAttachmentRepository;
import com.teamops.ticket.repository.TicketCategoryRepository;
import com.teamops.ticket.repository.TicketCommentRepository;
import com.teamops.ticket.repository.TicketHistoryRepository;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.ticket.repository.TicketSpecifications;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Help desk tickets (brief sections 11 and 12). Every change is checked with {@link TicketAccess} and written to
 * ticket_history; creation, assignment and status changes are also audited. Tickets the actor may not see are
 * reported as "not found".
 * <p>
 * SLA: due times come from the priority's policy and are snapshotted at creation. The clock pauses while the ticket
 * waits for the requester and while it is resolved or closed; reopening resumes it.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

	private final TicketRepository ticketRepository;

	private final TicketCategoryRepository categoryRepository;

	private final TicketCommentRepository commentRepository;

	private final TicketAttachmentRepository attachmentRepository;

	private final TicketHistoryRepository historyRepository;

	private final SlaPolicyRepository slaPolicyRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final CodeGenerator codeGenerator;

	private final AccessScopeService accessScopeService;

	private final AppSettingsService settings;

	private final NotificationService notificationService;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	// --- queries --------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<TicketListItem> search(TicketRequests.Search criteria, Pageable pageable,
			AuthenticatedUser actor) {
		Instant now = calendar.now();
		var spec = TicketSpecifications.visibleTo(access(actor))
			.and(TicketSpecifications.view(criteria.view(), actor.id()))
			.and(TicketSpecifications.matches(criteria.search()))
			.and(TicketSpecifications.statusIn(criteria.statuses()))
			.and(TicketSpecifications.priorityIn(criteria.priorities()))
			.and(TicketSpecifications.category(criteria.categoryId()))
			.and(TicketSpecifications.department(criteria.departmentId()))
			.and(TicketSpecifications.assignee(criteria.assigneeId()));
		return PageResponse.of(ticketRepository.findAll(spec, pageable).map(t -> TicketListItem.of(t, now)));
	}

	@Transactional(readOnly = true)
	public MyTicketSummary mySummary(AuthenticatedUser actor) {
		MyTicketCounts counts = ticketRepository.countForUser(actor.id(), TicketStatus.OPEN_STATUSES);
		return new MyTicketSummary(counts.getRequestedOpen(), counts.getWaitingOnMe(), counts.getResolvedToConfirm(),
				counts.getAssignedOpen());
	}

	@Transactional(readOnly = true)
	public java.util.List<CategoryResponse> categories() {
		return categoryRepository.findByActiveTrueOrderByNameAsc().stream().map(CategoryResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public TicketDetail get(Long id, AuthenticatedUser actor) {
		TicketAccess access = access(actor);
		return toDetail(loadVisible(id, access), access);
	}

	// --- commands -------------------------------------------------------------------------------------------

	@Transactional
	public TicketDetail create(TicketRequests.CreateTicket request, AuthenticatedUser actor, ClientInfo client) {
		TicketAccess access = access(actor);
		TicketCategory category = resolveCategory(request.categoryId());
		Department department = handlingDepartment(category, request.departmentId());
		TicketPriority priority = request.priority() == null ? TicketPriority.MEDIUM : request.priority();
		SlaPolicy policy = slaPolicyRepository.findByPriority(priority)
			.orElseThrow(() -> ApiException.conflict("SLA_POLICY_MISSING", "No SLA policy exists for " + priority));
		Instant now = calendar.now();

		Ticket ticket = new Ticket();
		ticket.setCode(codeGenerator.next(CodeGenerator.TICKET));
		ticket.setSubject(request.subject().trim());
		ticket.setDescription(trimToNull(request.description()));
		ticket.setRequester(userRepository.getReferenceById(actor.id()));
		ticket.setDepartment(department);
		ticket.setCategory(category);
		ticket.setPriority(priority);
		ticket.setSlaPolicy(policy);
		ticket.setSlaStartAt(now);
		ticket.setFirstResponseDueAt(now.plusSeconds(policy.getFirstResponseMinutes() * 60L));
		ticket.setResolutionDueAt(now.plusSeconds(policy.getResolutionMinutes() * 60L));
		ticket.setSlaWarningPct(settings.slaWarningThresholdPct());

		User assignee = resolveAssignee(request.assigneeId());
		if (assignee != null) {
			if (!access.canAssign(ticket, assignee)) {
				throw ApiException.forbidden("CANNOT_ASSIGN", "You cannot assign tickets in that department");
			}
			ticket.setAssignee(assignee);
			ticket.setStatus(TicketStatus.OPEN);
		}
		Ticket saved = ticketRepository.save(ticket);

		history(saved, actor, "created", null, saved.getCode());
		auditService.record(AuditAction.TICKET_CREATED, actor.id(), "TICKET", saved.getId(),
				Map.of("code", saved.getCode(), "departmentId", department.getId(), "priority", priority,
						"assigneeId", assignee == null ? "none" : assignee.getId()),
				client);
		notifyAssigned(saved, actor);
		return toDetail(saved, access);
	}

	@Transactional
	public TicketDetail update(Long id, TicketRequests.UpdateTicket request, AuthenticatedUser actor) {
		TicketAccess access = access(actor);
		Ticket ticket = loadVisible(id, access);
		requireWork(access, ticket);
		if (!Objects.equals(ticket.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this ticket just now. Reload and try again.");
		}
		String subject = request.subject().trim();
		track(ticket, actor, "subject", ticket.getSubject(), subject);
		ticket.setSubject(subject);
		String description = trimToNull(request.description());
		if (!Objects.equals(ticket.getDescription(), description)) {
			history(ticket, actor, "description", null, null);
			ticket.setDescription(description);
		}
		TicketCategory category = resolveCategory(request.categoryId());
		track(ticket, actor, "category", categoryName(ticket.getCategory()), category.getName());
		ticket.setCategory(category);
		track(ticket, actor, "priority", ticket.getPriority(), request.priority());
		ticket.setPriority(request.priority());

		if (!ticket.getDepartment().getId().equals(request.departmentId())) {
			Department department = resolveActiveDepartment(request.departmentId());
			history(ticket, actor, "department", ticket.getDepartment().getName(), department.getName());
			ticket.setDepartment(department);
			User assignee = ticket.getAssignee();
			if (assignee != null && !assignee.getDepartment().getId().equals(department.getId())) {
				history(ticket, actor, "assignee", assignee.getFullName(), null);
				ticket.setAssignee(null);
			}
		}
		ticketRepository.flush();
		return toDetail(ticket, access);
	}

	@Transactional
	public TicketDetail assign(Long id, Long assigneeId, AuthenticatedUser actor, ClientInfo client) {
		TicketAccess access = access(actor);
		Ticket ticket = loadVisible(id, access);
		User assignee = resolveAssignee(assigneeId);
		if (!access.canAssign(ticket, assignee)) {
			throw ApiException.forbidden("CANNOT_ASSIGN", "You cannot assign this ticket to that person");
		}
		if (Objects.equals(userId(ticket.getAssignee()), assigneeId)) {
			return toDetail(ticket, access);
		}
		Long previous = userId(ticket.getAssignee());
		history(ticket, actor, "assignee", nameOf(ticket.getAssignee()), nameOf(assignee));
		ticket.setAssignee(assignee);
		if (assignee != null && ticket.getStatus() == TicketStatus.NEW) {
			history(ticket, actor, "status", TicketStatus.NEW.name(), TicketStatus.OPEN.name());
			applyStatus(ticket, TicketStatus.OPEN, calendar.now());
		}
		auditService.record(AuditAction.TICKET_ASSIGNED, actor.id(), "TICKET", ticket.getId(),
				Map.of("code", ticket.getCode(), "from", previous == null ? "none" : previous, "to",
						assigneeId == null ? "none" : assigneeId),
				client);
		notifyAssigned(ticket, actor);
		ticketRepository.flush();
		return toDetail(ticket, access);
	}

	@Transactional
	public TicketDetail changeStatus(Long id, TicketStatus status, AuthenticatedUser actor, ClientInfo client) {
		TicketAccess access = access(actor);
		Ticket ticket = loadVisible(id, access);
		TicketStatus previous = ticket.getStatus();
		if (previous == status) {
			return toDetail(ticket, access);
		}
		if (!previous.canMoveTo(status)) {
			throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
					"A ticket cannot move from " + label(previous) + " to " + label(status));
		}
		if (!access.allowedStatuses(ticket).contains(status)) {
			throw ApiException.forbidden("FORBIDDEN", "You cannot move this ticket to " + label(status));
		}
		boolean reopened = previous.isDone() && !status.isDone();
		applyStatus(ticket, status, calendar.now());
		history(ticket, actor, reopened ? "reopened" : "status", previous.name(), status.name());
		auditService.record(AuditAction.TICKET_STATUS_CHANGED, actor.id(), "TICKET", ticket.getId(),
				Map.of("code", ticket.getCode(), "from", previous, "to", status, "reopened", reopened), client);

		String title = ticket.getCode() + " is now " + label(status);
		if (!ticket.isRequestedBy(actor.id()) && ticket.getRequester() != null) {
			notify(ticket.getRequester().getId(), NotificationType.TICKET_UPDATED, title, ticket);
		}
		else if (ticket.isRequestedBy(actor.id()) && ticket.getAssignee() != null
				&& !ticket.isAssignedTo(actor.id())) {
			notify(ticket.getAssignee().getId(), NotificationType.TICKET_UPDATED, title, ticket);
		}
		ticketRepository.flush();
		return toDetail(ticket, access);
	}

	/**
	 * Moves the ticket to {@code next} and keeps the SLA clock consistent:
	 * <ul>
	 * <li>OPEN / IN_PROGRESS / NEW: the clock runs (any pause ends).</li>
	 * <li>WAITING_FOR_REQUESTER: the clock pauses.</li>
	 * <li>RESOLVED: stamps the resolution (and the first response, if there was none) and pauses the clock.</li>
	 * <li>CLOSED: as RESOLVED if not yet resolved, and stamps the close time.</li>
	 * <li>Reopening a resolved or closed ticket clears the resolution; the time it spent resolved does not count.</li>
	 * </ul>
	 */
	static void applyStatus(Ticket ticket, TicketStatus next, Instant now) {
		TicketStatus previous = ticket.getStatus();
		if (next.clockRuns()) {
			ticket.resumeSla(now);
		}
		if (previous.isDone() && !next.isDone()) {
			ticket.setResolvedAt(null);
			ticket.setClosedAt(null);
		}
		switch (next) {
			case WAITING_FOR_REQUESTER -> ticket.pauseSla(now);
			case RESOLVED -> {
				ticket.setResolvedAt(now);
				stampFirstResponse(ticket, now);
				ticket.pauseSla(now);
			}
			case CLOSED -> {
				if (ticket.getResolvedAt() == null) {
					ticket.setResolvedAt(now);
				}
				stampFirstResponse(ticket, now);
				ticket.setClosedAt(now);
				ticket.pauseSla(now);
			}
			default -> {
			}
		}
		ticket.setStatus(next);
	}

	// --- shared helpers (also used by TicketCollaborationService and SlaService) ----------------------------

	public TicketAccess access(AuthenticatedUser actor) {
		return new TicketAccess(actor, accessScopeService.scopeFor(actor));
	}

	Ticket loadVisible(Long id, TicketAccess access) {
		return ticketRepository.findDetailedById(id)
			.filter(access::canView)
			.orElseThrow(() -> ApiException.notFound("TICKET_NOT_FOUND", "Ticket not found"));
	}

	static void requireWork(TicketAccess access, Ticket ticket) {
		if (!access.canWork(ticket)) {
			throw ApiException.forbidden("FORBIDDEN", "You do not have permission to work on this ticket");
		}
	}

	void history(Ticket ticket, AuthenticatedUser actor, String field, Object oldValue, Object newValue) {
		TicketHistory entry = new TicketHistory();
		entry.setTicket(ticket);
		entry.setChangedBy(actor == null ? null : userRepository.getReferenceById(actor.id()));
		entry.setFieldName(field);
		entry.setOldValue(display(oldValue));
		entry.setNewValue(display(newValue));
		historyRepository.save(entry);
	}

	void notify(Long userId, NotificationType type, String title, Ticket ticket) {
		notificationService.notify(userId, type, title, ticket.getSubject(), NotificationService.ENTITY_TICKET,
				ticket.getId(), null);
	}

	static void stampFirstResponse(Ticket ticket, Instant now) {
		if (ticket.getFirstRespondedAt() == null) {
			ticket.setFirstRespondedAt(now);
		}
	}

	/** Internal notes are only included for people who can work on the ticket. */
	TicketDetail toDetail(Ticket ticket, TicketAccess access) {
		Long id = ticket.getId();
		boolean canWork = access.canWork(ticket);
		return new TicketDetail(id, ticket.getCode(), ticket.getSubject(), ticket.getDescription(),
				ticket.getStatus(), ticket.getPriority(), CategoryRef.of(ticket.getCategory()),
				DepartmentSummary.of(ticket.getDepartment()), UserSummary.of(ticket.getRequester()),
				UserSummary.of(ticket.getAssignee()),
				ticket.getSlaPolicy() == null ? null : ticket.getSlaPolicy().getName(),
				TicketSla.of(ticket, calendar.now()), ticket.getCreatedAt(), ticket.getUpdatedAt(),
				ticket.getFirstRespondedAt(), ticket.getResolvedAt(), ticket.getClosedAt(), ticket.getVersion(),
				commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(id)
					.stream()
					.filter(comment -> canWork || !comment.isInternal())
					.map(comment -> CommentResponse.of(comment, ticket))
					.toList(),
				attachmentRepository.findByTicketIdOrderByAddedAtAsc(id).stream().map(AttachmentResponse::of).toList(),
				historyRepository.findTop100ByTicketIdOrderByChangedAtDescIdDesc(id)
					.stream()
					.map(HistoryEntry::of)
					.toList(),
				new TicketPermissions(canWork,
						access.canManage(ticket) || (canWork && access.actor().hasPermission(TicketAccess.TICKET_ASSIGN)),
						canWork, access.allowedStatuses(ticket)));
	}

	/** "Waiting for requester", "In progress", ... */
	static String label(TicketStatus status) {
		String text = status.name().replace('_', ' ').toLowerCase(Locale.ROOT);
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	// --- private helpers ------------------------------------------------------------------------------------

	private void notifyAssigned(Ticket ticket, AuthenticatedUser actor) {
		if (ticket.getAssignee() != null && !ticket.isAssignedTo(actor.id())) {
			notificationService.notify(ticket.getAssignee().getId(), NotificationType.TICKET_ASSIGNED,
					ticket.getCode() + " assigned to you",
					actor.fullName() + " assigned you \"" + ticket.getSubject() + "\"",
					NotificationService.ENTITY_TICKET, ticket.getId(), null);
		}
	}

	private void track(Ticket ticket, AuthenticatedUser actor, String field, Object oldValue, Object newValue) {
		if (!Objects.equals(oldValue, newValue)) {
			history(ticket, actor, field, oldValue, newValue);
		}
	}

	private TicketCategory resolveCategory(Long categoryId) {
		return categoryRepository.findById(categoryId)
			.filter(TicketCategory::isActive)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_CATEGORY", "Category not found"));
	}

	/** The category's team, or the chosen department when the category has none. */
	private Department handlingDepartment(TicketCategory category, Long departmentId) {
		if (category.getDefaultDepartment() != null) {
			return resolveActiveDepartment(category.getDefaultDepartment().getId());
		}
		if (departmentId == null) {
			throw ApiException.badRequest("DEPARTMENT_REQUIRED", "Choose the team that should handle this ticket");
		}
		return resolveActiveDepartment(departmentId);
	}

	private Department resolveActiveDepartment(Long departmentId) {
		Department department = departmentRepository.findById(departmentId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		if (department.getStatus() != DepartmentStatus.ACTIVE) {
			throw ApiException.badRequest("DEPARTMENT_INACTIVE", "Tickets cannot be sent to an inactive department");
		}
		return department;
	}

	private User resolveAssignee(Long assigneeId) {
		if (assigneeId == null) {
			return null;
		}
		User user = userRepository.findWithDepartmentById(assigneeId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "Assignee not found"));
		if (!user.isActive()) {
			throw ApiException.badRequest("USER_DISABLED", "Tickets cannot be assigned to a disabled user");
		}
		return user;
	}

	private static String display(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString();
		return text.length() > TicketHistory.MAX_VALUE_LENGTH ? text.substring(0, TicketHistory.MAX_VALUE_LENGTH)
				: text;
	}

	private static String categoryName(TicketCategory category) {
		return category == null ? null : category.getName();
	}

	private static String nameOf(User user) {
		return user == null ? null : user.getFullName();
	}

	private static Long userId(User user) {
		return user == null ? null : user.getId();
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
