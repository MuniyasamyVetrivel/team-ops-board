package com.teamops.approval.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.teamops.approval.entity.Approval;
import com.teamops.approval.entity.ApprovalStatus;
import com.teamops.approval.entity.ApprovalStep;
import com.teamops.approval.entity.ApprovalTypeStep;
import com.teamops.approval.entity.ApproverKind;
import com.teamops.approval.entity.StepStatus;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;

/**
 * The approval workflow rules (brief section 14), as pure functions over the entities:
 * <ul>
 * <li>On submit the type's template is copied into the request. A DEPARTMENT_MANAGER step is decided by the
 * requester's department manager; when there is none, or the requester is the manager, it escalates to the
 * escalation role (Super Admin).</li>
 * <li>Nobody approves their own request: a USER step naming the requester is skipped, and two consecutive steps for
 * the same role collapse into one.</li>
 * <li>Steps run in order. Approving moves to the next step; approving the last one approves the request. Rejecting
 * (with a reason) ends it and skips the rest. The requester may cancel while it is pending.</li>
 * </ul>
 */
public final class ApprovalEngine {

	static final String APPROVAL_DECIDE = "APPROVAL_DECIDE";

	private ApprovalEngine() {
	}

	/** Builds the request's steps from the template. Nothing is pending yet: call {@link #advance}. */
	public static List<ApprovalStep> materialize(Approval approval, List<ApprovalTypeStep> template,
			User departmentManager, Role escalationRole) {
		Long requesterId = approval.getRequester().getId();
		List<ApprovalStep> steps = new ArrayList<>();
		Role previousRole = null;
		for (ApprovalTypeStep source : template) {
			ApprovalStep step = new ApprovalStep();
			step.setApproval(approval);
			step.setStepOrder(steps.size() + 1);
			switch (source.getApproverKind()) {
				case DEPARTMENT_MANAGER -> {
					if (departmentManager == null || departmentManager.getId().equals(requesterId)) {
						step.setApproverKind(ApproverKind.ROLE);
						step.setApproverRole(escalationRole);
					}
					else {
						step.setApproverKind(ApproverKind.DEPARTMENT_MANAGER);
						step.setApprover(departmentManager);
					}
				}
				case ROLE -> {
					step.setApproverKind(ApproverKind.ROLE);
					step.setApproverRole(source.getApproverRole());
				}
				case USER -> {
					step.setApproverKind(ApproverKind.USER);
					step.setApprover(source.getApproverUser());
					if (source.getApproverUser() == null || source.getApproverUser().getId().equals(requesterId)) {
						step.setStatus(StepStatus.SKIPPED);
					}
				}
			}
			if (step.getStatus() != StepStatus.SKIPPED && step.getApproverRole() != null && previousRole != null
					&& Objects.equals(previousRole.getId(), step.getApproverRole().getId())) {
				step.setStatus(StepStatus.SKIPPED);
			}
			if (step.getStatus() != StepStatus.SKIPPED) {
				previousRole = step.getApproverRole();
			}
			steps.add(step);
		}
		return steps;
	}

	/** Makes the next waiting step pending, or approves the request when none is left. */
	public static Optional<ApprovalStep> advance(Approval approval, Instant now) {
		Optional<ApprovalStep> next = approval.getSteps()
			.stream()
			.filter(step -> step.getStatus() == StepStatus.WAITING)
			.findFirst();
		if (next.isPresent()) {
			next.get().setStatus(StepStatus.PENDING);
			approval.setCurrentStep(next.get().getStepOrder());
		}
		else {
			approval.setStatus(ApprovalStatus.APPROVED);
			approval.setCurrentStep(null);
			approval.setDecidedAt(now);
		}
		return next;
	}

	/** Whether the actor may decide the given (pending) step. */
	public static boolean canDecide(Approval approval, ApprovalStep step, AuthenticatedUser actor) {
		if (approval.getStatus() != ApprovalStatus.PENDING || step.getStatus() != StepStatus.PENDING
				|| approval.isRequestedBy(actor.id()) || !actor.hasPermission(APPROVAL_DECIDE)) {
			return false;
		}
		if (step.getApprover() != null) {
			return step.getApprover().getId().equals(actor.id());
		}
		return step.getApproverRole() != null && actor.hasRole(step.getApproverRole().getCode());
	}

	/**
	 * Records the decision on the pending step. Returns the next pending step after an approval, or empty when the
	 * request is finished (approved or rejected).
	 */
	public static Optional<ApprovalStep> decide(Approval approval, AuthenticatedUser actor, User decidedBy,
			boolean approve, String comment, Instant now) {
		ApprovalStep step = approval.pendingStep()
			.orElseThrow(() -> ApiException.conflict("NOT_PENDING", "This request is no longer waiting for a decision"));
		if (!canDecide(approval, step, actor)) {
			throw ApiException.forbidden("NOT_YOUR_DECISION", "This request is not waiting for your decision");
		}
		if (!approve && (comment == null || comment.isBlank())) {
			throw ApiException.badRequest("REASON_REQUIRED", "Give a reason when rejecting a request");
		}
		step.setStatus(approve ? StepStatus.APPROVED : StepStatus.REJECTED);
		step.setDecidedBy(decidedBy);
		step.setComment(comment == null || comment.isBlank() ? null : comment.trim());
		step.setDecidedAt(now);
		if (approve) {
			return advance(approval, now);
		}
		skipRemaining(approval);
		approval.setStatus(ApprovalStatus.REJECTED);
		approval.setCurrentStep(null);
		approval.setDecidedAt(now);
		return Optional.empty();
	}

	public static void cancel(Approval approval, Instant now) {
		if (approval.getStatus() != ApprovalStatus.PENDING) {
			throw ApiException.conflict("NOT_PENDING", "Only pending requests can be cancelled");
		}
		approval.pendingStep().ifPresent(step -> step.setStatus(StepStatus.SKIPPED));
		skipRemaining(approval);
		approval.setStatus(ApprovalStatus.CANCELLED);
		approval.setCurrentStep(null);
		approval.setDecidedAt(now);
	}

	private static void skipRemaining(Approval approval) {
		approval.getSteps()
			.stream()
			.filter(step -> step.getStatus() == StepStatus.WAITING)
			.forEach(step -> step.setStatus(StepStatus.SKIPPED));
	}

}
