package com.teamops.marketing.paid.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.marketing.paid.service.PaidResults;
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

/** One campaign's results for one month (unique per campaign, month and year); rates are computed. */
@Getter
@Setter
@Entity
@Table(name = "paid_campaign_metrics")
public class PaidCampaignMonth extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campaign_id", nullable = false, updatable = false)
	private PaidCampaign campaign;

	@Column(name = "month", nullable = false, updatable = false)
	private Integer month;

	@Column(name = "year", nullable = false, updatable = false)
	private Integer year;

	@Column(name = "amount_spent", nullable = false, precision = 14, scale = 2)
	private BigDecimal amountSpent = BigDecimal.ZERO;

	@Column(name = "impressions", nullable = false)
	private int impressions;

	@Column(name = "clicks", nullable = false)
	private int clicks;

	@Column(name = "leads", nullable = false)
	private int leads;

	@Column(name = "conversions", nullable = false)
	private int conversions;

	@Column(name = "notes", length = 1000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "source", nullable = false, length = 32)
	private AdDataSource source = AdDataSource.MANUAL;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recorded_by")
	private User recordedBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public PaidResults results() {
		return new PaidResults(amountSpent, impressions, clicks, leads, conversions);
	}

	public void apply(PaidResults results) {
		amountSpent = results.spend();
		impressions = (int) results.impressions();
		clicks = (int) results.clicks();
		leads = (int) results.leads();
		conversions = (int) results.conversions();
	}

}
