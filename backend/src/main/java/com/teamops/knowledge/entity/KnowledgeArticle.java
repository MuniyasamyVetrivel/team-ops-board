package com.teamops.knowledge.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
import com.teamops.tag.Tag;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** A Markdown article. The slug is set once from the title and stays stable, so links keep working. */
@Getter
@Setter
@Entity
@Table(name = "knowledge_articles")
public class KnowledgeArticle extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private KnowledgeCategory category;

	@Column(name = "title", nullable = false, length = 250)
	private String title;

	@Column(name = "slug", nullable = false, length = 270)
	private String slug;

	@Column(name = "body", nullable = false, columnDefinition = "mediumtext")
	private String body;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private ArticleStatus status = ArticleStatus.DRAFT;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id")
	private User author;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "view_count", nullable = false)
	private int viewCount;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@ManyToMany
	@JoinTable(name = "article_tags", joinColumns = @JoinColumn(name = "article_id"),
			inverseJoinColumns = @JoinColumn(name = "tag_id"))
	private Set<Tag> tags = new HashSet<>();

	public boolean isAuthoredBy(Long userId) {
		return author != null && author.getId().equals(userId);
	}

}
