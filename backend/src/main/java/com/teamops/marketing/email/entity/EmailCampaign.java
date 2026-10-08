package com.teamops.marketing.email.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.marketing.email.service.EmailCounts;
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

/** An email campaign with its raw counts (brief section 37). Rates are computed by {@code EmailRates}. */
@Getter
@Setter
@Entity
@Table(name = "email_campaigns")
public class EmailCampaign extends BaseEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "campaign_type", nullable = false, length = 32)
	private EmailCampaignType campaignType;

	@Column(name = "campaign_date", nullable = false)
	private LocalDate campaignDate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Column(name = "audience", length = 200)
	private String audience;

	@Column(name = "emails_sent", nullable = false)
	private int emailsSent;

	@Column(name = "delivered", nullable = false)
	private int delivered;

	@Column(name = "bounced", nullable = false)
	private int bounced;

	@Column(name = "opened", nullable = false)
	private int opened;

	@Column(name = "unique_opens", nullable = false)
	private int uniqueOpens;

	@Column(name = "clicked", nullable = false)
	private int clicked;

	@Column(name = "unique_clicks", nullable = false)
	private int uniqueClicks;

	@Column(name = "unsubscribed", nullable = false)
	private int unsubscribed;

	@Column(name = "leads_generated", nullable = false)
	private int leadsGenerated;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private EmailCampaignStatus status = EmailCampaignStatus.DRAFT;

	@Column(name = "notes", length = 2000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "provider", nullable = false, length = 32)
	private CampaignProvider provider = CampaignProvider.MANUAL;

	@Column(name = "external_id", length = 100)
	private String externalId;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public EmailCounts counts() {
		return new EmailCounts(emailsSent, delivered, bounced, opened, uniqueOpens, clicked, uniqueClicks,
				unsubscribed, leadsGenerated);
	}

	public void apply(EmailCounts counts) {
		emailsSent = counts.emailsSent();
		delivered = counts.delivered();
		bounced = counts.bounced();
		opened = counts.opened();
		uniqueOpens = counts.uniqueOpens();
		clicked = counts.clicked();
		uniqueClicks = counts.uniqueClicks();
		unsubscribed = counts.unsubscribed();
		leadsGenerated = counts.leads();
	}

}
