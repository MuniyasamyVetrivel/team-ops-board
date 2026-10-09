package com.teamops.marketing.report.entity;

import java.time.Instant;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A frozen monthly report: the team-wide report as JSON, kept as it stood when the month was frozen. */
@Getter
@Setter
@Entity
@Table(name = "marketing_monthly_reports")
public class MarketingMonthlyReport extends BaseEntity {

	@Column(name = "report_month", nullable = false, updatable = false)
	private Integer month;

	@Column(name = "report_year", nullable = false, updatable = false)
	private Integer year;

	@Column(name = "payload", nullable = false, updatable = false, columnDefinition = "json")
	private String payload;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "generated_by", updatable = false)
	private User generatedBy;

	@Column(name = "generated_at", nullable = false, updatable = false)
	private Instant generatedAt;

}
