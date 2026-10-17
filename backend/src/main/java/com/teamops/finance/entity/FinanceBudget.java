package com.teamops.finance.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLRestriction;

import com.teamops.common.audit.AuditFinance;
import com.teamops.common.persistence.SoftDeletableEntity;
import com.teamops.department.entity.Department;

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
 * A department's budget for a month, in AUD (the base currency); one per department and month. Actual spend,
 * remaining, used % and over-budget are computed from the expenses ({@code BudgetMath}), never stored.
 */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "finance_budgets")
public class FinanceBudget extends SoftDeletableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@Column(name = "budget_year", nullable = false)
	private int year;

	@Column(name = "budget_month", nullable = false)
	private int month;

	@AuditFinance
	@Column(name = "amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
