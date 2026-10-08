package com.teamops.approval.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.teamops.approval.entity.Approval;
import com.teamops.approval.entity.ApprovalStatus;
import com.teamops.approval.entity.ApprovalStep;
import com.teamops.approval.entity.ApprovalType;
import com.teamops.approval.entity.ApprovalTypeStep;
import com.teamops.approval.entity.ApproverKind;
import com.teamops.approval.entity.StepStatus;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Approval API records. */
public final class ApprovalDtos {

	private ApprovalDtos() {
	}

	public enum View {

		/** Everything the viewer can see. */
		ALL,
		/** Requests the viewer raised. */
		MINE,
		/** Pending requests waiting for the viewer's decision. */
		TO_DECIDE

	}

	public enum Decision {

		APPROVE, REJECT

	}

	public record RoleRef(String code, String name) {

		public static RoleRef of(Role role) {
			return role == null ? null : new RoleRef(role.getCode(), role.getName());
		}

	}

	public record TypeRef(Long id, String code, String name, boolean requiresAmount) {

		public static TypeRef of(ApprovalType type) {
			return new TypeRef(type.getId(), type.getCode(), type.getName(), type.isRequiresAmount());
		}

	}

	public record TemplateStep(int stepOrder, ApproverKind approverKind, RoleRef role, UserSummary user) {

		public static TemplateStep of(ApprovalTypeStep step) {
			return new TemplateStep(step.getStepOrder(), step.getApproverKind(), RoleRef.of(step.getApproverRole()),
					UserSummary.of(step.getApproverUser()));
		}

	}

	public record TypeResponse(Long id, String code, String name, String description, boolean requiresAmount,
			boolean active, List<TemplateStep> steps) {

		public static TypeResponse of(ApprovalType type) {
			return new TypeResponse(type.getId(), type.getCode(), type.getName(), type.getDescription(),
					type.isRequiresAmount(), type.isActive(), type.getSteps().stream().map(TemplateStep::of).toList());
		}

	}

	public record StepResponse(int stepOrder, ApproverKind approverKind, UserSummary approver, RoleRef role,
			StepStatus status, UserSummary decidedBy, String comment, Instant decidedAt) {

		public static StepResponse of(ApprovalStep step) {
			return new StepResponse(step.getStepOrder(), step.getApproverKind(), UserSummary.of(step.getApprover()),
					RoleRef.of(step.getApproverRole()), step.getStatus(), UserSummary.of(step.getDecidedBy()),
					step.getComment(), step.getDecidedAt());
		}

	}

	/**
	 * @param waitingOn who decides the current step: a person's name or a role name ({@code null} when finished)
	 * @param awaitingMe whether the viewer can decide it now
	 */
	public record ApprovalListItem(Long id, String code, String title, TypeRef type, ApprovalStatus status,
			UserSummary requester, DepartmentSummary department, BigDecimal amount, String currency,
			LocalDate dueDate, Integer currentStep, int stepCount, String waitingOn, boolean awaitingMe,
			Instant createdAt, Instant decidedAt) {

		public static ApprovalListItem of(Approval approval, boolean awaitingMe) {
			return new ApprovalListItem(approval.getId(), approval.getCode(), approval.getTitle(),
					TypeRef.of(approval.getType()), approval.getStatus(), UserSummary.of(approval.getRequester()),
					DepartmentSummary.of(approval.getDepartment()), approval.getAmount(), approval.getCurrency(),
					approval.getDueDate(), approval.getCurrentStep(), approval.getSteps().size(), waitingOn(approval),
					awaitingMe, approval.getCreatedAt(), approval.getDecidedAt());
		}

		static String waitingOn(Approval approval) {
			return approval.pendingStep()
				.map(step -> step.getApprover() != null ? step.getApprover().getFullName()
						: step.getApproverRole() != null ? "Any " + step.getApproverRole().getName() : null)
				.orElse(null);
		}

	}

	public record ApprovalPermissions(boolean canDecide, boolean canCancel) {

	}

	public record ApprovalDetail(Long id, String code, String title, String description, TypeRef type,
			ApprovalStatus status, UserSummary requester, DepartmentSummary department, BigDecimal amount,
			String currency, LocalDate dueDate, Integer currentStep, List<StepResponse> steps, Instant createdAt,
			Instant decidedAt, Integer version, ApprovalPermissions permissions) {

	}

	/** {@code amount} is required when the type asks for one. */
	public record Submit(
			@NotNull(message = "Type is required") Long typeId,
			@NotBlank(message = "Title is required") @Size(max = 250) String title,
			@Size(max = 20000) String description,
			@DecimalMin(value = "0", message = "Amount cannot be negative") @DecimalMax(value = "999999999999.99", message = "Amount is too large") BigDecimal amount,
			@Pattern(regexp = "[A-Z]{3}", message = "Use a 3-letter currency code") String currency,
			LocalDate dueDate) {

	}

	/** A reason is required when rejecting. */
	public record Decide(@NotNull(message = "Decision is required") Decision decision,
			@Size(max = 5000) String comment) {

	}

	public record WorkflowStep(@NotNull(message = "Approver kind is required") ApproverKind approverKind,
			String roleCode, Long userId) {

	}

	/** Replaces a type's workflow. Applies to new requests only. */
	public record UpdateWorkflow(
			@NotEmpty(message = "At least one step is required") @Size(max = 5, message = "At most 5 steps") List<@Valid WorkflowStep> steps) {

	}

	public record Search(String search, Set<ApprovalStatus> statuses, Long typeId, View view) {

		public Search {
			statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
			view = view == null ? View.ALL : view;
		}

	}

}
