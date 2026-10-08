package com.teamops.knowledge.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.knowledge.dto.KnowledgeDtos.ArticleDetail;
import com.teamops.knowledge.dto.KnowledgeDtos.ArticleListItem;
import com.teamops.knowledge.dto.KnowledgeDtos.CategoryResponse;
import com.teamops.knowledge.dto.KnowledgeDtos.SaveArticle;
import com.teamops.knowledge.dto.KnowledgeDtos.Search;
import com.teamops.knowledge.entity.ArticleStatus;
import com.teamops.knowledge.service.KnowledgeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Knowledge base API: KB_VIEW reads published articles; KB_EDIT writes and manages drafts. */
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('KB_VIEW')")
public class KnowledgeController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("updated", List.of("updatedAt"), "published",
			List.of("publishedAt", "id"), "title", List.of("title"), "views", List.of("viewCount", "id"));

	private final KnowledgeService knowledgeService;

	@GetMapping("/categories")
	public List<CategoryResponse> categories() {
		return knowledgeService.categories();
	}

	@GetMapping("/articles")
	public PageResponse<ArticleListItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Set<ArticleStatus> status, @RequestParam(required = false) String tag,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser actor) {
		return knowledgeService.search(new Search(search, categoryId, status, tag),
				PageRequests.of(page, size, sort, SORT_FIELDS, "updated,desc"), actor);
	}

	@GetMapping("/articles/{slug}")
	public ArticleDetail get(@PathVariable String slug, @AuthenticationPrincipal AuthenticatedUser actor) {
		return knowledgeService.get(slug, actor);
	}

	@PostMapping("/articles")
	@PreAuthorize("hasAuthority('KB_EDIT')")
	public ResponseEntity<ArticleDetail> create(@Valid @RequestBody SaveArticle request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(knowledgeService.save(null, request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/articles/{id}")
	@PreAuthorize("hasAuthority('KB_EDIT')")
	public ArticleDetail update(@PathVariable Long id, @Valid @RequestBody SaveArticle request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return knowledgeService.save(id, request, actor, ClientInfo.from(http));
	}

	@PostMapping(path = "/articles/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('KB_EDIT')")
	public ArticleDetail addAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return knowledgeService.addAttachment(id, file, actor);
	}

	/** Always served as a download. */
	@GetMapping("/articles/{id}/attachments/{fileId}")
	public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		KnowledgeService.Download download = knowledgeService.download(id, fileId, actor);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.contentType()))
			.contentLength(download.sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(download.content());
	}

	@DeleteMapping("/articles/{id}/attachments/{fileId}")
	@PreAuthorize("hasAuthority('KB_EDIT')")
	public ArticleDetail deleteAttachment(@PathVariable Long id, @PathVariable Long fileId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return knowledgeService.deleteAttachment(id, fileId, actor);
	}

}
