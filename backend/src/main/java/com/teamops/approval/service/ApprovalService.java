package com.teamops.approval.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.approval.dto.ApprovalDtos;
import com.teamops.approval.dto.ApprovalDtos.ApprovalDetail;
import com.teamops.approval.dto.ApprovalDtos.ApprovalListItem;
import com.teamops.approval.dto.ApprovalDtos.ApprovalPermissions;
import com.teamops.approval.dto.ApprovalDtos.StepResponse;
import com.teamops.approval.dto.ApprovalDtos.TypeRef;
import com.teamops.approval.dto.ApprovalDtos.TypeResponse;
import com.teamops.approval.entity.Approval;
import com.teamops.approval.entity.ApprovalStatus;
import com.teamops.approval.entity.ApprovalStep;
import com.teamops.approval.entity.ApprovalType;
import com.teamops.approval.entity.ApprovalTypeStep;
import com.teamops.approval.entity.ApproverKind;
import com.teamops.approval.repository.ApprovalRepository;
import com.teamops.approval.repository.ApprovalSpecifications;
import com.teamops.approval.repository.ApprovalTypeRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.service.NotificationService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.RoleRepository;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Approval requests and their workflows (brief section 14). The rules live in {@link ApprovalEngine}; this service
 * loads, checks visibility, notifies and audits. Requests the actor cannot see are reported as not found.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

	private final ApprovalRepository approvalRepository;

	private final ApprovalTypeRepository typeRepository;

	private final UserRepository userRepository;

	private final RoleRepository roleRepository;

	private final CodeGenerator codeGenerator;

	private final AccessScopeService accessScopeService;

	private final NotificationService notificationService;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	// --- types and workflows --------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<TypeResponse> types() {
		return typeRepository.findAllByOrderByNameAsc().stream().map(TypeResponse::of).toList();
	}

	/** Adds a request type with its workflow (brief: "Configure workflows"). */
	@Transactional
	public TypeResponse createType(ApprovalDtos.CreateType request, AuthenticatedUser actor, ClientInfo client) {
		if (typeRepository.existsByCode(request.code())) {
			throw ApiException.conflict("APPROVAL_TYPE_CODE_TAKEN", "Another approval type already uses this code");
		}
		ApprovalType type = new ApprovalType();
		type.setCode(request.code());
		type.setName(request.name().trim());
		type.setDescription(blankToNull(request.description()));
		type.setRequiresAmount(Boolean.TRUE.equals(request.requiresAmount()));
		type.setActive(true);
		replaceSteps(type, request.steps());
		ApprovalType saved = typeRepository.saveAndFlush(type);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("code", saved.getCode());
		details.put("name", saved.getName());
		details.put("requiresAmount", saved.isRequiresAmount());
		details.put("steps", saved.getSteps().stream().map(ApprovalService::describe).toList());
		auditService.record(AuditAction.APPROVAL_TYPE_CREATED, actor.id(), "APPROVAL_TYPE", saved.getId(), details,
				client);
		return TypeResponse.of(saved);
	}

	/** Changes a type's details. Deactivating keeps existing requests but stops new ones. */
	@Transactional
	public TypeResponse updateType(Long typeId, ApprovalDtos.UpdateType request, AuthenticatedUser actor,
			ClientInfo client) {
		ApprovalType type = loadType(typeId);
		if (!Objects.equals(type.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE",
					"Someone else changed this approval type just now. Reload and try again.");
		}
		String name = request.name().trim();
		String description = blankToNull(request.description());
		AuditChanges changes = new AuditChanges().track("name", type.getName(), name)
			.track("description", type.getDescription(), description)
			.track("requiresAmount", type.isRequiresAmount(), request.requiresAmount())
			.track("active", type.isActive(), request.active());
		if (!changes.isEmpty()) {
			type.setName(name);
			type.setDescription(description);
			type.setRequiresAmount(request.requiresAmount());
			type.setActive(request.active());
			typeRepository.flush();
			auditService.record(AuditAction.APPROVAL_TYPE_UPDATED, actor.id(), "APPROVAL_TYPE", type.getId(),
					changes.toDetails(), client);
		}
		return TypeResponse.of(type);
	}

	/** Replaces a type's workflow. Requests already submitted keep their own copy of the steps. */
	@Transactional
	public TypeResponse updateWorkflow(Long typeId, ApprovalDtos.UpdateWorkflow request, AuthenticatedUser actor,
			ClientInfo client) {
		ApprovalType type = loadType(typeId);
		List<String> before = type.getSteps().stream().map(ApprovalService::describe).toList();
		type.getSteps().clear();
		typeRepository.flush(); // delete old rows first: (type, step_order) is unique
		replaceSteps(type, request.steps());
		typeRepository.flush();
		List<String> after = type.getSteps().stream().map(ApprovalService::describe).toList();
		if (!before.equals(after)) {
			auditService.record(AuditAction.APPROVAL_WORKFLOW_UPDATED, actor.id(), "APPROVAL_TYPE", type.getId(),
					Map.of("changes", Map.of("steps", Map.of("from", before, "to", after))), client);
		}
		return TypeResponse.of(type);
	}

	private ApprovalType loadType(Long typeId) {
		return typeRepository.findById(typeId)
			.orElseThrow(() -> ApiException.notFound("APPROVAL_TYPE_NOT_FOUND", "Approval type not found"));
	}

	private void replaceSteps(ApprovalType type, List<ApprovalDtos.WorkflowStep> steps) {
		int order = 1;
		for (ApprovalDtos.WorkflowStep input : steps) {
			ApprovalTypeStep step = new ApprovalTypeStep();
			step.setApprovalType(type);
			step.setStepOrder(order++);
			step.setApproverKind(input.approverKind());
			switch (input.approverKind()) {
				case ROLE -> step.setApproverRole(roleRepository.findByCode(String.valueOf(input.roleCode()))
					.orElseThrow(() -> ApiException.badRequest("UNKNOWN_ROLE", "Choose a role for each role step")));
				case USER -> step.setApproverUser(activeUser(input.userId()));
				case DEPARTMENT_MANAGER -> {
				}
			}
			type.getSteps().add(step);
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	// --- requests -------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<ApprovalListItem> search(ApprovalDtos.Search criteria, Pageable pageable,
			AuthenticatedUser actor) {
		var spec = ApprovalSpecifications.visibleTo(actor, accessScopeService.scopeFor(actor))
			.and(switch (criteria.view()) {
				case ALL -> ApprovalSpecifications.all();
				case MINE -> ApprovalSpecifications.requestedBy(actor.id());
				case TO_DECIDE -> ApprovalSpecifications.awaitingDecisionBy(actor);
			})
			.and(ApprovalSpecifications.statusIn(criteria.statuses()))
			.and(ApprovalSpecifications.type(criteria.typeId()))
			.and(ApprovalSpecifications.matches(criteria.search()));
		return PageResponse.of(approvalRepository.findAll(spec, pageable)
			.map(approval -> ApprovalListItem.of(approval, awaitingMe(approval, actor))));
	}

	@Transactional(readOnly = true)
	public ApprovalDetail get(Long id, AuthenticatedUser actor) {
		return toDetail(loadVisible(id, actor), actor);
	}

	@Transactional
	public ApprovalDetail submit(ApprovalDtos.Submit request, AuthenticatedUser actor, ClientInfo client) {
		ApprovalType type = typeRepository.findById(request.typeId())
			.filter(ApprovalType::isActive)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_APPROVAL_TYPE", "Approval type not found"));
		if (type.isRequiresAmount() && request.amount() == null) {
			throw ApiException.badRequest("AMOUNT_REQUIRED", type.getName() + " requests need an amount");
		}
		User requester = userRepository.findWithDepartmentById(actor.id())
			.orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Your account was not found"));
		Instant now = calendar.now();

		Approval approval = new Approval();
		approval.setCode(codeGenerator.next(CodeGenerator.APPROVAL));
		approval.setType(type);
		approval.setTitle(request.title().trim());
		approval.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
		approval.setRequester(requester);
		approval.setDepartment(requester.getDepartment());
		approval.setAmount(request.amount());
		approval.setCurrency(request.currency() == null ? "INR" : request.currency());
		approval.setDueDate(request.dueDate());
		approval.getSteps()
			.addAll(ApprovalEngine.materialize(approval, type.getSteps(), requester.getDepartment().getManager(),
					escalationRole()));
		Optional<ApprovalStep> pending = ApprovalEngine.advance(approval, now);
		Approval saved = approvalRepository.save(approval);

		auditService.record(AuditAction.APPROVAL_SUBMITTED, actor.id(), "APPROVAL", saved.getId(),
				Map.of("code", saved.getCode(), "type", type.getCode(), "amount", amountText(request.amount())),
				client);
		pending.ifPresentOrElse(step -> notifyApprovers(saved, step), () -> notifyRequester(saved));
		return toDetail(saved, actor);
	}

	@Transactional
	public ApprovalDetail decide(Long id, ApprovalDtos.Decide request, AuthenticatedUser actor, ClientInfo client) {
		Approval approval = loadVisible(id, actor);
		int step = approval.getCurrentStep() == null ? 0 : approval.getCurrentStep();
		boolean approve = request.decision() == ApprovalDtos.Decision.APPROVE;
		Optional<ApprovalStep> next = ApprovalEngine.decide(approval, actor, userRepository.getReferenceById(actor.id()),
				approve, request.comment(), calendar.now());
		approvalRepository.flush();
		auditService.record(AuditAction.APPROVAL_DECIDED, actor.id(), "APPROVAL", approval.getId(),
				Map.of("code", approval.getCode(), "step", step, "decision", request.decision(), "status",
						approval.getStatus()),
				client);
		next.ifPresentOrElse(pending -> notifyApprovers(approval, pending), () -> notifyRequester(approval));
		return toDetail(approval, actor);
	}

	@Transactional
	public ApprovalDetail cancel(Long id, AuthenticatedUser actor, ClientInfo client) {
		Approval approval = loadVisible(id, actor);
		if (!approval.isRequestedBy(actor.id())) {
			throw ApiException.forbidden("FORBIDDEN", "Only the requester can cancel a request");
		}
		ApprovalEngine.cancel(approval, calendar.now());
		approvalRepository.flush();
		auditService.record(AuditAction.APPROVAL_CANCELLED, actor.id(), "APPROVAL", approval.getId(),
				Map.of("code", approval.getCode()), client);
		return toDetail(approval, actor);
	}

	/** Pending requests the viewer can see (dashboard KPI). */
	@Transactional(readOnly = true)
	public long pendingCount(AuthenticatedUser actor) {
		return approvalRepository.count(ApprovalSpecifications.visibleTo(actor, accessScopeService.scopeFor(actor))
			.and(ApprovalSpecifications.statusIn(java.util.Set.of(ApprovalStatus.PENDING))));
	}

	/** Pending requests with a due date in the range that the viewer can see (calendar). */
	@Transactional(readOnly = true)
	public List<Approval> pendingDueBetween(LocalDate from, LocalDate to, AuthenticatedUser actor) {
		return approvalRepository.findAll(ApprovalSpecifications.visibleTo(actor, accessScopeService.scopeFor(actor))
			.and(ApprovalSpecifications.statusIn(java.util.Set.of(ApprovalStatus.PENDING)))
			.and(ApprovalSpecifications.dueBetween(from, to)));
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private Approval loadVisible(Long id, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		return approvalRepository.findDetailedById(id)
			.filter(approval -> canView(approval, actor, scope))
			.orElseThrow(() -> ApiException.notFound("APPROVAL_NOT_FOUND", "Request not found"));
	}

	static boolean canView(Approval approval, AuthenticatedUser actor, AccessScope scope) {
		return scope.isAll() || approval.isRequestedBy(actor.id())
				|| scope.coversDepartment(approval.getDepartment().getId())
				|| approval.getSteps()
					.stream()
					.anyMatch(step -> (step.getApprover() != null && step.getApprover().getId().equals(actor.id()))
							|| (step.getApproverRole() != null && actor.hasRole(step.getApproverRole().getCode())));
	}

	private static boolean awaitingMe(Approval approval, AuthenticatedUser actor) {
		return approval.pendingStep().map(step -> ApprovalEngine.canDecide(approval, step, actor)).orElse(false);
	}

	private ApprovalDetail toDetail(Approval approval, AuthenticatedUser actor) {
		return new ApprovalDetail(approval.getId(), approval.getCode(), approval.getTitle(),
				approval.getDescription(), TypeRef.of(approval.getType()), approval.getStatus(),
				UserSummary.of(approval.getRequester()), DepartmentSummary.of(approval.getDepartment()),
				approval.getAmount(), approval.getCurrency(), approval.getDueDate(), approval.getCurrentStep(),
				approval.getSteps().stream().map(StepResponse::of).toList(), approval.getCreatedAt(),
				approval.getDecidedAt(), approval.getVersion(),
				new ApprovalPermissions(awaitingMe(approval, actor),
						approval.isRequestedBy(actor.id()) && approval.getStatus() == ApprovalStatus.PENDING));
	}

	/** The approver of a pending step, or every active holder of its role (never the requester). */
	private void notifyApprovers(Approval approval, ApprovalStep step) {
		List<User> approvers = step.getApprover() != null ? List.of(step.getApprover())
				: userRepository.findByRoleAndStatus(step.getApproverRole().getCode(), UserStatus.ACTIVE);
		String requester = approval.getRequester() == null ? "Someone" : approval.getRequester().getFullName();
		approvers.stream()
			.filter(user -> !approval.isRequestedBy(user.getId()))
			.forEach(user -> notificationService.notify(user.getId(), NotificationType.APPROVAL_REQUIRED,
					approval.getCode() + " needs your approval", requester + ": " + approval.getTitle(),
					NotificationService.ENTITY_APPROVAL, approval.getId(), null));
	}

	private void notifyRequester(Approval approval) {
		if (approval.getRequester() == null) {
			return;
		}
		String outcome = approval.getStatus() == ApprovalStatus.APPROVED ? "approved" : "rejected";
		notificationService.notify(approval.getRequester().getId(), NotificationType.APPROVAL_DECIDED,
				approval.getCode() + " was " + outcome, approval.getTitle(), NotificationService.ENTITY_APPROVAL,
				approval.getId(), null);
	}

	private Role escalationRole() {
		return roleRepository.findByCode(RoleCodes.SUPER_ADMIN)
			.orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role is missing"));
	}

	private User activeUser(Long userId) {
		if (userId == null) {
			throw ApiException.badRequest("UNKNOWN_USER", "Choose a person for each named-approver step");
		}
		return userRepository.findById(userId)
			.filter(User::isActive)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "Approver not found or disabled"));
	}

	private static String describe(ApprovalTypeStep step) {
		return switch (step.getApproverKind()) {
			case DEPARTMENT_MANAGER -> ApproverKind.DEPARTMENT_MANAGER.name();
			case ROLE -> "ROLE:" + (step.getApproverRole() == null ? "?" : step.getApproverRole().getCode());
			case USER -> "USER:" + (step.getApproverUser() == null ? "?" : step.getApproverUser().getId());
		};
	}

	private static String amountText(BigDecimal amount) {
		return amount == null ? "none" : amount.toPlainString();
	}

}
