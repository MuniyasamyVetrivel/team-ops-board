package com.teamops.marketing.seo.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
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
 * A keyword a page targets, unique per page, search engine, location and device. {@code currentPosition},
 * {@code previousPosition} and {@code lastRankedAt} cache the latest two ranking history rows (NULL position = Not
 * Ranked). Only {@code SeoKeywordRepository#refreshRankingCache} writes them.
 */
@Getter
@Setter
@Entity
@Table(name = "marketing_keywords")
public class SeoKeyword extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "page_id", nullable = false)
	private SeoPage page;

	@Column(name = "keyword", nullable = false, length = 200)
	private String keyword;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "search_engine", nullable = false, length = 32)
	private SearchEngine searchEngine = SearchEngine.GOOGLE;

	@Column(name = "location", nullable = false, length = 100)
	private String location;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "device", nullable = false, length = 16)
	private Device device = Device.DESKTOP;

	@Column(name = "target_position")
	private Integer targetPosition;

	@Column(name = "current_position", insertable = false, updatable = false)
	private Integer currentPosition;

	@Column(name = "previous_position", insertable = false, updatable = false)
	private Integer previousPosition;

	@Column(name = "search_volume")
	private Integer searchVolume;

	@Column(name = "keyword_difficulty")
	private Integer keywordDifficulty;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private KeywordStatus status = KeywordStatus.ACTIVE;

	@Column(name = "last_ranked_at", insertable = false, updatable = false)
	private Instant lastRankedAt;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
