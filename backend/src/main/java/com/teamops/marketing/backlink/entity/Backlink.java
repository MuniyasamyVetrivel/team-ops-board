package com.teamops.marketing.backlink.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.marketing.seo.entity.SeoPage;
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
 * A backlink (brief section 45): a link from a referring site to one of our pages. Each stage it reached keeps its
 * date ({@link BacklinkDates}); the monthly tracking counts the stages in the month of those dates.
 */
@Getter
@Setter
@Entity
@Table(name = "backlinks")
public class Backlink extends BaseEntity {

	@Column(name = "code", nullable = false, length = 20, updatable = false)
	private String code;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_page_id")
	private SeoPage targetPage;

	@Column(name = "target_url", nullable = false, length = 500)
	private String targetUrl;

	@Column(name = "referring_domain", nullable = false, length = 255)
	private String referringDomain;

	@Column(name = "link_url", length = 700)
	private String linkUrl;

	@Column(name = "anchor_text", length = 255)
	private String anchorText;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "link_type", nullable = false, length = 32)
	private BacklinkType linkType = BacklinkType.GUEST_POST;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private BacklinkStatus status = BacklinkStatus.PROSPECTED;

	@Column(name = "submitted_date")
	private LocalDate submittedDate;

	@Column(name = "approved_date")
	private LocalDate approvedDate;

	@Column(name = "live_date")
	private LocalDate liveDate;

	@Column(name = "rejected_date")
	private LocalDate rejectedDate;

	@Column(name = "lost_date")
	private LocalDate lostDate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Column(name = "domain_authority")
	private Integer domainAuthority;

	@Column(name = "notes", length = 2000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "provider", nullable = false, length = 32, updatable = false)
	private BacklinkOrigin provider = BacklinkOrigin.MANUAL;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", updatable = false)
	private User createdBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public BacklinkDates dates() {
		return new BacklinkDates(submittedDate, approvedDate, liveDate, rejectedDate, lostDate);
	}

	public void setDates(BacklinkDates dates) {
		submittedDate = dates.submitted();
		approvedDate = dates.approved();
		liveDate = dates.live();
		rejectedDate = dates.rejected();
		lostDate = dates.lost();
	}

}
