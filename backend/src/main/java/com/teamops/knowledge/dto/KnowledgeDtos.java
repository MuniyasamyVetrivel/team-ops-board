package com.teamops.knowledge.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.knowledge.entity.ArticleAttachment;
import com.teamops.knowledge.entity.ArticleStatus;
import com.teamops.knowledge.entity.KnowledgeArticle;
import com.teamops.knowledge.entity.KnowledgeCategory;
import com.teamops.knowledge.service.KnowledgeText;
import com.teamops.tag.Tag;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Knowledge base API records. */
public final class KnowledgeDtos {

	private KnowledgeDtos() {
	}

	static final int EXCERPT_LENGTH = 220;

	public record CategoryRef(Long id, String name, String slug) {

		public static CategoryRef of(KnowledgeCategory category) {
			return new CategoryRef(category.getId(), category.getName(), category.getSlug());
		}

	}

	public record CategoryResponse(Long id, String name, String slug, String description, long articleCount) {

	}

	public record ArticleListItem(Long id, String title, String slug, String excerpt, CategoryRef category,
			ArticleStatus status, UserSummary author, List<String> tags, Instant publishedAt, Instant updatedAt,
			int viewCount) {

		public static ArticleListItem of(KnowledgeArticle article) {
			return new ArticleListItem(article.getId(), article.getTitle(), article.getSlug(),
					KnowledgeText.excerpt(article.getBody(), EXCERPT_LENGTH), CategoryRef.of(article.getCategory()),
					article.getStatus(), UserSummary.of(article.getAuthor()), tagNames(article.getTags()),
					article.getPublishedAt(), article.getUpdatedAt(), article.getViewCount());
		}

	}

	public record AttachmentResponse(Long fileId, String fileName, String contentType, long sizeBytes,
			UserSummary addedBy, Instant addedAt) {

		public static AttachmentResponse of(ArticleAttachment attachment) {
			return new AttachmentResponse(attachment.getFile().getId(), attachment.getFile().getOriginalName(),
					attachment.getFile().getContentType(), attachment.getFile().getSizeBytes(),
					UserSummary.of(attachment.getAddedBy()), attachment.getAddedAt());
		}

	}

	public record ArticleDetail(Long id, String title, String slug, String body, CategoryRef category,
			ArticleStatus status, UserSummary author, DepartmentSummary department, List<String> tags,
			Instant publishedAt, Instant createdAt, Instant updatedAt, int viewCount, Integer version,
			List<AttachmentResponse> attachments, boolean canEdit) {

	}

	/** Create when {@code version} is null; otherwise a full update. Publishing stamps the first publish time. */
	public record SaveArticle(
			Integer version,
			@NotBlank(message = "Title is required") @Size(max = 250) String title,
			@NotBlank(message = "Content is required") @Size(max = 200000, message = "The article is too long") String body,
			@NotNull(message = "Category is required") Long categoryId,
			Long departmentId,
			@Size(max = 10, message = "At most 10 tags") List<@Size(max = 40) String> tags,
			@NotNull(message = "Status is required") ArticleStatus status) {

	}

	public record Search(String search, Long categoryId, Set<ArticleStatus> statuses, String tag) {

		public Search {
			statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
		}

	}

	static List<String> tagNames(Set<Tag> tags) {
		return tags.stream().map(Tag::getName).sorted().toList();
	}

}
