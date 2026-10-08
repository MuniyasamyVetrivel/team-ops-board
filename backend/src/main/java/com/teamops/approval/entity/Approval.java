package com.teamops.approval.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
import com.teamops.user.entity.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** A request moving through its own copy of the workflow steps. */
@Getter
@Setter
@Entity
@Table(name = "approvals")
public class Approval extends BaseEntity {

	@Column(name = "code", nullable = false, length = 20)
	private String code;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "approval_type_id", nullable = false)
	private ApprovalType type;

	@Column(name = "title", nullable = false, length = 250)
	private String title;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requester_id")
	private User requester;

	/** The requester's department at the time of the request. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@Column(name = "amount", precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "INR";

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private ApprovalStatus status = ApprovalStatus.PENDING;

	@Column(name = "current_step")
	private Integer currentStep;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@OneToMany(mappedBy = "approval", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("stepOrder")
	private List<ApprovalStep> steps = new ArrayList<>();

	public boolean isRequestedBy(Long userId) {
		return requester != null && requester.getId().equals(userId);
	}

	/** The step waiting for a decision, if any. */
	public Optional<ApprovalStep> pendingStep() {
		return steps.stream().filter(step -> step.getStatus() == StepStatus.PENDING).findFirst();
	}

}
