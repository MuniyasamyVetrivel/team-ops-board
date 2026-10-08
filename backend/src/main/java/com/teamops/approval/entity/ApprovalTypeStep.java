package com.teamops.approval.entity;

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

/** One step of an approval type's workflow template. */
@Getter
@Setter
@Entity
@Table(name = "approval_type_steps")
public class ApprovalTypeStep {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "approval_type_id", nullable = false)
	private ApprovalType approvalType;

	@Column(name = "step_order", nullable = false)
	private int stepOrder;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "approver_kind", nullable = false, length = 32)
	private ApproverKind approverKind;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approver_role_id")
	private Role approverRole;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approver_user_id")
	private User approverUser;

}
