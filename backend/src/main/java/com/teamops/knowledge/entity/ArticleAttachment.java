package com.teamops.knowledge.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import com.teamops.common.storage.StoredFile;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "article_attachments")
public class ArticleAttachment {

	@EmbeddedId
	private Id id;

	@MapsId("articleId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "article_id")
	private KnowledgeArticle article;

	@MapsId("fileId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id")
	private StoredFile file;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "added_by")
	private User addedBy;

	@CreationTimestamp
	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	public static ArticleAttachment of(KnowledgeArticle article, StoredFile file, User addedBy) {
		ArticleAttachment attachment = new ArticleAttachment();
		attachment.setId(new Id(article.getId(), file.getId()));
		attachment.setArticle(article);
		attachment.setFile(file);
		attachment.setAddedBy(addedBy);
		return attachment;
	}

	@Getter
	@Embeddable
	@NoArgsConstructor
	public static class Id implements Serializable {

		@Column(name = "article_id")
		private Long articleId;

		@Column(name = "file_id")
		private Long fileId;

		public Id(Long articleId, Long fileId) {
			this.articleId = articleId;
			this.fileId = fileId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Id that && Objects.equals(articleId, that.articleId)
					&& Objects.equals(fileId, that.fileId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(articleId, fileId);
		}

	}

}
