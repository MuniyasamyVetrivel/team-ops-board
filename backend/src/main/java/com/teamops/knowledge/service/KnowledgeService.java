package com.teamops.knowledge.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.knowledge.dto.KnowledgeDtos.ArticleDetail;
import com.teamops.knowledge.dto.KnowledgeDtos.ArticleListItem;
import com.teamops.knowledge.dto.KnowledgeDtos.AttachmentResponse;
import com.teamops.knowledge.dto.KnowledgeDtos.CategoryRef;
import com.teamops.knowledge.dto.KnowledgeDtos.CategoryResponse;
import com.teamops.knowledge.dto.KnowledgeDtos.SaveArticle;
import com.teamops.knowledge.dto.KnowledgeDtos.Search;
import com.teamops.knowledge.entity.ArticleAttachment;
import com.teamops.knowledge.entity.ArticleStatus;
import com.teamops.knowledge.entity.KnowledgeArticle;
import com.teamops.knowledge.entity.KnowledgeCategory;
import com.teamops.knowledge.repository.ArticleAttachmentRepository;
import com.teamops.knowledge.repository.KnowledgeArticleRepository;
import com.teamops.knowledge.repository.KnowledgeCategoryRepository;
import com.teamops.tag.Tag;
import com.teamops.tag.TagService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.repository.UserRepository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * Knowledge base (brief section 16). Published articles are readable by everyone with KB_VIEW; drafts and archived
 * articles only by editors (KB_EDIT) and their author. Editors can edit any article: the knowledge base is shared.
 * Search combines the FULLTEXT index (title + body, prefix matching) with a title substring match.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

	static final String KB_EDIT = "KB_EDIT";

	private final KnowledgeArticleRepository articleRepository;

	private final KnowledgeCategoryRepository categoryRepository;

	private final ArticleAttachmentRepository attachmentRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final TagService tagService;

	private final FileService fileService;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	@Transactional(readOnly = true)
	public List<CategoryResponse> categories() {
		Map<Long, Long> counts = new HashMap<>();
		articleRepository.countPublishedByCategory().forEach(c -> counts.put(c.getCategoryId(), c.getTotal()));
		return categoryRepository.findAllByOrderByPositionAscNameAsc()
			.stream()
			.map(c -> new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(),
					counts.getOrDefault(c.getId(), 0L)))
			.toList();
	}

	@Transactional(readOnly = true)
	public PageResponse<ArticleListItem> search(Search criteria, Pageable pageable, AuthenticatedUser actor) {
		Specification<KnowledgeArticle> spec = visibleTo(actor);
		if (!criteria.statuses().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("status").in(criteria.statuses()));
		}
		if (criteria.categoryId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), criteria.categoryId()));
		}
		if (StringUtils.hasText(criteria.tag())) {
			String tag = criteria.tag().trim().toLowerCase(Locale.ROOT);
			spec = spec.and((root, query, cb) -> {
				Subquery<Long> tagged = query.subquery(Long.class);
				Root<KnowledgeArticle> article = tagged.from(KnowledgeArticle.class);
				Join<KnowledgeArticle, Tag> tags = article.join("tags");
				tagged.select(article.get("id")).where(cb.equal(tags.get("name"), tag));
				return root.get("id").in(tagged);
			});
		}
		if (StringUtils.hasText(criteria.search())) {
			spec = spec.and(matches(criteria.search()));
		}
		return PageResponse.of(articleRepository.findAll(spec, pageable).map(ArticleListItem::of));
	}

	/** Opening a published article counts a view. */
	@Transactional
	public ArticleDetail get(String slug, AuthenticatedUser actor) {
		KnowledgeArticle article = articleRepository.findBySlug(slug)
			.filter(a -> canView(a, actor))
			.orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "Article not found"));
		if (article.getStatus() == ArticleStatus.PUBLISHED) {
			articleRepository.incrementViews(article.getId());
			article.setViewCount(article.getViewCount() + 1);
		}
		return toDetail(article, actor);
	}

	@Transactional
	public ArticleDetail save(Long id, SaveArticle request, AuthenticatedUser actor, ClientInfo client) {
		KnowledgeArticle article;
		if (id == null) {
			article = new KnowledgeArticle();
			article.setAuthor(userRepository.getReferenceById(actor.id()));
			article.setSlug(uniqueSlug(request.title()));
		}
		else {
			article = editable(id, actor);
			if (!Objects.equals(article.getVersion(), request.version())) {
				throw ApiException.conflict("STALE_UPDATE", "Someone else changed this article just now. Reload and try again.");
			}
		}
		KnowledgeCategory category = categoryRepository.findById(request.categoryId())
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_CATEGORY", "Category not found"));
		boolean publishing = request.status() == ArticleStatus.PUBLISHED && article.getStatus() != ArticleStatus.PUBLISHED;
		article.setTitle(request.title().trim());
		article.setBody(request.body().trim());
		article.setCategory(category);
		article.setDepartment(request.departmentId() == null ? null
				: departmentRepository.findById(request.departmentId())
					.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found")));
		article.setTags(new java.util.HashSet<>(tagService.resolve(request.tags())));
		article.setStatus(request.status());
		if (publishing && article.getPublishedAt() == null) {
			article.setPublishedAt(calendar.now());
		}
		KnowledgeArticle saved = articleRepository.saveAndFlush(article);
		if (publishing) {
			auditService.record(AuditAction.ARTICLE_PUBLISHED, actor.id(), "ARTICLE", saved.getId(),
					Map.of("title", saved.getTitle(), "slug", saved.getSlug()), client);
		}
		return toDetail(saved, actor);
	}

	@Transactional
	public ArticleDetail addAttachment(Long id, MultipartFile upload, AuthenticatedUser actor) {
		KnowledgeArticle article = editable(id, actor);
		StoredFile file = fileService.store(upload, actor.id());
		attachmentRepository.save(ArticleAttachment.of(article, file, userRepository.getReferenceById(actor.id())));
		return toDetail(article, actor);
	}

	@Transactional(readOnly = true)
	public Download download(Long id, Long fileId, AuthenticatedUser actor) {
		articleRepository.findDetailedById(id)
			.filter(a -> canView(a, actor))
			.orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "Article not found"));
		StoredFile file = loadAttachment(id, fileId).getFile();
		return new Download(file.getOriginalName(), file.getContentType(), file.getSizeBytes(), fileService.content(file));
	}

	@Transactional
	public ArticleDetail deleteAttachment(Long id, Long fileId, AuthenticatedUser actor) {
		KnowledgeArticle article = editable(id, actor);
		ArticleAttachment attachment = loadAttachment(id, fileId);
		attachmentRepository.delete(attachment);
		fileService.delete(attachment.getFile());
		return toDetail(article, actor);
	}

	public record Download(String fileName, String contentType, long sizeBytes, Resource content) {
	}

	// --- helpers --------------------------------------------------------------------------------------------

	static boolean canView(KnowledgeArticle article, AuthenticatedUser actor) {
		return article.getStatus() == ArticleStatus.PUBLISHED || actor.hasPermission(KB_EDIT)
				|| article.isAuthoredBy(actor.id());
	}

	static boolean canEdit(AuthenticatedUser actor) {
		return actor.hasPermission(KB_EDIT);
	}

	private static Specification<KnowledgeArticle> visibleTo(AuthenticatedUser actor) {
		if (actor.hasPermission(KB_EDIT)) {
			return (root, query, cb) -> cb.conjunction();
		}
		return (root, query, cb) -> cb.or(cb.equal(root.get("status"), ArticleStatus.PUBLISHED),
				cb.equal(root.get("author").get("id"), actor.id()));
	}

	/** Full-text hits (any length ≥ 3 words, as prefixes) or a title substring. */
	private Specification<KnowledgeArticle> matches(String search) {
		String booleanQuery = KnowledgeText.booleanQuery(search);
		List<Long> ids = booleanQuery == null ? List.of() : articleRepository.fullTextIds(booleanQuery);
		String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
			.replace("_", "\\_") + "%";
		return (root, query, cb) -> {
			var title = cb.like(cb.lower(root.get("title")), pattern, '\\');
			return ids.isEmpty() ? title : cb.or(title, root.get("id").in(ids));
		};
	}

	private KnowledgeArticle editable(Long id, AuthenticatedUser actor) {
		KnowledgeArticle article = articleRepository.findDetailedById(id)
			.filter(a -> canView(a, actor))
			.orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "Article not found"));
		if (!canEdit(actor)) {
			throw ApiException.forbidden("FORBIDDEN", "You cannot edit knowledge base articles");
		}
		return article;
	}

	private String uniqueSlug(String title) {
		String base = KnowledgeText.slugify(title);
		String slug = base;
		for (int i = 2; articleRepository.existsBySlug(slug); i++) {
			slug = base + "-" + i;
		}
		return slug;
	}

	private ArticleAttachment loadAttachment(Long articleId, Long fileId) {
		return attachmentRepository.findById(new ArticleAttachment.Id(articleId, fileId))
			.orElseThrow(() -> ApiException.notFound("ATTACHMENT_NOT_FOUND", "Attachment not found"));
	}

	private ArticleDetail toDetail(KnowledgeArticle article, AuthenticatedUser actor) {
		return new ArticleDetail(article.getId(), article.getTitle(), article.getSlug(), article.getBody(),
				CategoryRef.of(article.getCategory()), article.getStatus(), UserSummary.of(article.getAuthor()),
				article.getDepartment() == null ? null : DepartmentSummary.of(article.getDepartment()),
				article.getTags().stream().map(Tag::getName).sorted().toList(), article.getPublishedAt(),
				article.getCreatedAt(), article.getUpdatedAt(), article.getViewCount(), article.getVersion(),
				attachmentRepository.findByArticleIdOrderByAddedAtAsc(article.getId())
					.stream()
					.map(AttachmentResponse::of)
					.toList(),
				canEdit(actor));
	}

}
