package com.teamops.marketing.target.entity;

import java.math.BigDecimal;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * One type's target for one month (unique per type, month and year). Achievement, remaining and status are computed
 * by {@code TargetProgress}. {@code actualValue} is the hand-entered actual; automatic types compute theirs.
 */
@Getter
@Setter
@Entity
@Table(name = "marketing_targets")
public class MarketingTarget extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_type_id", nullable = false, updatable = false)
	private TargetType type;

	@Column(name = "month", nullable = false, updatable = false)
	private Integer month;

	@Column(name = "year", nullable = false, updatable = false)
	private Integer year;

	@Column(name = "target_value", nullable = false, precision = 14, scale = 2)
	private BigDecimal targetValue;

	@Column(name = "actual_value", precision = 14, scale = 2)
	private BigDecimal actualValue;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@Column(name = "notes", length = 1000)
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
