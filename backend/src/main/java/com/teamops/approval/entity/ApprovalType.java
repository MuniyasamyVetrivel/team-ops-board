package com.teamops.approval.entity;

import java.util.ArrayList;
import java.util.List;

import com.teamops.common.persistence.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** A kind of request with its configurable approval workflow (the step template). */
@Getter
@Setter
@Entity
@Table(name = "approval_types")
public class ApprovalType extends BaseEntity {

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "requires_amount", nullable = false)
	private boolean requiresAmount;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@OneToMany(mappedBy = "approvalType", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("stepOrder")
	private List<ApprovalTypeStep> steps = new ArrayList<>();

}
