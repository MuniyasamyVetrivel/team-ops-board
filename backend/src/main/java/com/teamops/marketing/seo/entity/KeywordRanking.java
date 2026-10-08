package com.teamops.marketing.seo.entity;

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
 * One keyword's position for one month (unique per keyword, month and year); earlier months are never overwritten.
 * {@code position} is {@code null} when the keyword is not ranked. {@code previousPosition} and {@code rankingChange}
 * are a snapshot taken when the month was recorded; services compute movement from the rows themselves.
 */
@Getter
@Setter
@Entity
@Table(name = "keyword_ranking_history")
public class KeywordRanking extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "keyword_id", nullable = false, updatable = false)
	private SeoKeyword keyword;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "page_id", nullable = false)
	private SeoPage page;

	@Column(name = "ranking_month", nullable = false, updatable = false)
	private Integer month;

	@Column(name = "ranking_year", nullable = false, updatable = false)
	private Integer year;

	@Column(name = "ranking_position")
	private Integer position;

	@Column(name = "previous_position")
	private Integer previousPosition;

	@Column(name = "ranking_change")
	private Integer rankingChange;

	@Column(name = "search_volume")
	private Integer searchVolume;

	@Column(name = "notes", length = 1000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "source", nullable = false, length = 32)
	private RankingSource source = RankingSource.MANUAL;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recorded_by")
	private User recordedBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
