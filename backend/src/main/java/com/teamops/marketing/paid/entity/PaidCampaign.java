package com.teamops.marketing.paid.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** A paid campaign's plan (brief section 40). Its results are recorded per month in {@link PaidCampaignMonth}. */
@Getter
@Setter
@Entity
@Table(name = "paid_campaigns")
public class PaidCampaign extends BaseEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "platform", nullable = false, length = 32)
	private AdPlatform platform = AdPlatform.LINKEDIN;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "objective", nullable = false, length = 32)
	private CampaignObjective objective;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "budget", nullable = false, precision = 14, scale = 2)
	private BigDecimal budget;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "INR";

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private PaidCampaignStatus status = PaidCampaignStatus.DRAFT;

	@Column(name = "notes", length = 2000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "provider", nullable = false, length = 32)
	private AdDataSource provider = AdDataSource.MANUAL;

	@Column(name = "external_id", length = 100)
	private String externalId;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	/** Whether the campaign's dates overlap the month (an open-ended campaign runs on). */
	public boolean runsIn(MarketingPeriod period) {
		return !startDate.isAfter(period.lastDay()) && (endDate == null || !endDate.isBefore(period.firstDay()));
	}

}
