package com.teamops.approval.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One step of a request. USER and DEPARTMENT_MANAGER steps name an approver; ROLE steps can be decided by any
 * active holder of the role (except the requester).
 */
@Getter
@Setter
@Entity
@Table(name = "approval_steps")
public class ApprovalStep {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "approval_id", nullable = false)
	private Approval approval;

	@Column(name = "step_order", nullable = false)
	private int stepOrder;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "approver_kind", nullable = false, length = 32)
	private ApproverKind approverKind;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approver_id")
	private User approver;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approver_role_id")
	private Role approverRole;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private StepStatus status = StepStatus.WAITING;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by")
	private User decidedBy;

	@Column(name = "comment", columnDefinition = "text")
	private String comment;

	@Column(name = "decided_at")
	private Instant decidedAt;

}
