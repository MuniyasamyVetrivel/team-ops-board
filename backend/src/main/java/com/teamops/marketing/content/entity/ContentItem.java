package com.teamops.marketing.content.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.marketing.seo.entity.SeoKeyword;
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
 * A piece of marketing content (brief section 47). While it is live (PUBLISHED or UPDATED) it counts in the month of
 * its publication date; a blog counts towards the monthly blog target. Leads link to the content that brought them in.
 */
@Getter
@Setter
@Entity
@Table(name = "content_items")
public class ContentItem extends BaseEntity {

	@Column(name = "title", nullable = false, length = 300)
	private String title;

	@Column(name = "url", length = 1000)
	private String url;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "content_type", nullable = false, length = 32)
	private ContentType contentType = ContentType.BLOG;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id")
	private User author;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	/** The month it is planned for (any status); may be in the future. */
	@Column(name = "planned_date")
	private LocalDate plannedDate;

	@Column(name = "publication_date")
	private LocalDate publicationDate;

	/** When an UPDATED item was last refreshed. */
	@Column(name = "refreshed_date")
	private LocalDate refreshedDate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_keyword_id")
	private SeoKeyword targetKeyword;

	@Column(name = "target_keyword_text", length = 255)
	private String targetKeywordText;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_page_id")
	private SeoPage targetPage;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private ContentStatus status = ContentStatus.IDEA;

	@Column(name = "organic_traffic")
	private Integer organicTraffic;

	@Column(name = "cta_clicks")
	private Integer ctaClicks;

	@Column(name = "notes", length = 2000)
	private String notes;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", updatable = false)
	private User createdBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
