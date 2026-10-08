package com.teamops.marketing.seo.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.seo.dto.SeoDtos.CreateKeyword;
import com.teamops.marketing.seo.dto.SeoDtos.KeywordItem;
import com.teamops.marketing.seo.dto.SeoDtos.UpdateKeyword;
import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.service.SeoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * SEO keywords, each with its standing (position, status, movement) for {@code month}/{@code year}, defaulting to
 * the current business month.
 */
@RestController
@RequestMapping("/api/marketing/keywords")
@RequiredArgsConstructor
public class SeoKeywordController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("keyword", List.of("keyword", "id"), "page",
			List.of("page.title", "keyword"), "volume", List.of("searchVolume", "keyword"), "difficulty",
			List.of("keywordDifficulty", "keyword"), "target", List.of("targetPosition", "keyword"), "updated",
			List.of("updatedAt"));

	private final SeoService seoService;

	@GetMapping
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public PageResponse<KeywordItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long pageId, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Set<KeywordStatus> status, @RequestParam(required = false) Device device,
			@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort) {
		return seoService.searchKeywords(search, pageId, ownerId, status, device, seoService.period(month, year),
				PageRequests.of(page, size, sort, SORT_FIELDS, "keyword,asc"));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public KeywordItem get(@PathVariable Long id, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year) {
		return seoService.getKeyword(id, seoService.period(month, year));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('SEO_EDIT')")
	public ResponseEntity<KeywordItem> create(@Valid @RequestBody CreateKeyword request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(seoService.createKeyword(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('SEO_EDIT')")
	public KeywordItem update(@PathVariable Long id, @Valid @RequestBody UpdateKeyword request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return seoService.updateKeyword(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('SEO_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		seoService.deleteKeyword(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
