package com.teamops.celebration.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.marketing.email.entity.EmailCampaign;
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

/** An upcoming event, webinar, event marketing or email marketing activity. Times are UTC instants. */
@Getter
@Setter
@Entity
@Table(name = "marketing_events")
public class MarketingEvent extends BaseEntity {

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "event_type", nullable = false, length = 32)
	private MarketingEventType eventType;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private MarketingEventStatus status = MarketingEventStatus.PLANNED;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at")
	private Instant endAt;

	@Column(name = "location", length = 255)
	private String location;

	@Column(name = "online_link", length = 500)
	private String onlineLink;

	@Column(name = "registration_link", length = 500)
	private String registrationLink;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "email_campaign_id")
	private EmailCampaign emailCampaign;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public boolean isCancelled() {
		return status == MarketingEventStatus.CANCELLED;
	}

}
