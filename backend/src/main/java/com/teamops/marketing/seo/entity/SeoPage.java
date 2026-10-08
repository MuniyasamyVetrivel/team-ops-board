package com.teamops.marketing.seo.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
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

/** A website page tracked for SEO. Its statistics are computed from its keywords' ranking history. */
@Getter
@Setter
@Entity
@Table(name = "marketing_pages")
public class SeoPage extends BaseEntity {

	@Column(name = "url", nullable = false, length = 500)
	private String url;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "page_type", nullable = false, length = 32)
	private PageType pageType;

	@Column(name = "primary_keyword", length = 200)
	private String primaryKeyword;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private PageStatus status = PageStatus.ACTIVE;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
