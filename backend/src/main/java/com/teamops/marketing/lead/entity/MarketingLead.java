package com.teamops.marketing.lead.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.content.entity.ContentItem;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.paid.entity.PaidCampaign;
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

/**
 * A marketing lead (brief section 43). It counts in the month of its lead date towards the Website Leads target and
 * its source's target, and may name the email campaign, paid campaign or content item that brought it in.
 */
@Getter
@Setter
@Entity
@Table(name = "marketing_leads")
public class MarketingLead extends BaseEntity {

	@Column(name = "code", nullable = false, length = 20, updatable = false)
	private String code;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "company", length = 200)
	private String company;

	@Column(name = "email", length = 255)
	private String email;

	@Column(name = "phone", length = 50)
	private String phone;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "source", nullable = false, length = 32)
	private LeadSource source;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "email_campaign_id")
	private EmailCampaign emailCampaign;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "paid_campaign_id")
	private PaidCampaign paidCampaign;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "content_item_id")
	private ContentItem contentItem;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	@Column(name = "lead_date", nullable = false)
	private LocalDate leadDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private LeadStatus status = LeadStatus.NEW;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Column(name = "notes", length = 2000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "provider", nullable = false, length = 32, updatable = false)
	private LeadOrigin provider = LeadOrigin.MANUAL;

	@Column(name = "external_id", length = 100, updatable = false)
	private String externalId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", updatable = false)
	private User createdBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public MarketingPeriod period() {
		return MarketingPeriod.of(leadDate);
	}

}
