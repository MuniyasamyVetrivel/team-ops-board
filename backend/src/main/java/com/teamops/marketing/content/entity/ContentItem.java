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
 * A piece of marketing content (brief section 45). Leads link to the content that brought them in; the content
 * module itself (CRUD and monthly figures) arrives in Phase 18.
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

	@Column(name = "publication_date")
	private LocalDate publicationDate;

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

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
