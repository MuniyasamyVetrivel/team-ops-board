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
import com.teamops.marketing.seo.dto.SeoDtos.CreatePage;
import com.teamops.marketing.seo.dto.SeoDtos.PageDetail;
import com.teamops.marketing.seo.dto.SeoDtos.PageListItem;
import com.teamops.marketing.seo.dto.SeoDtos.PageRef;
import com.teamops.marketing.seo.dto.SeoDtos.UpdatePage;
import com.teamops.marketing.seo.entity.PageStatus;
import com.teamops.marketing.seo.entity.PageType;
import com.teamops.marketing.seo.service.SeoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * SEO website pages. Statistics are for {@code month}/{@code year}, defaulting to the current business month. The
 * whole {@code /api/marketing/**} group also requires MARKETING_VIEW.
 */
@RestController
@RequestMapping("/api/marketing/pages")
@RequiredArgsConstructor
public class SeoPageController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("title", List.of("title", "id"), "url",
			List.of("url"), "type", List.of("pageType", "title"), "status", List.of("status", "title"), "updated",
			List.of("updatedAt"), "created", List.of("createdAt"));

	private final SeoService seoService;

	@GetMapping
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public PageResponse<PageListItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<PageType> type, @RequestParam(required = false) Set<PageStatus> status,
			@RequestParam(required = false) Long ownerId, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort) {
		return seoService.searchPages(search, type, status, ownerId, departmentId, seoService.period(month, year),
				PageRequests.of(page, size, sort, SORT_FIELDS, "title,asc"));
	}

	@GetMapping("/options")
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public List<PageRef> options() {
		return seoService.pageOptions();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public PageDetail get(@PathVariable Long id, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @AuthenticationPrincipal AuthenticatedUser actor) {
		return seoService.getPage(id, seoService.period(month, year), actor);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('SEO_EDIT')")
	public ResponseEntity<PageDetail> create(@Valid @RequestBody CreatePage request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(seoService.createPage(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('SEO_EDIT')")
	public PageDetail update(@PathVariable Long id, @Valid @RequestBody UpdatePage request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return seoService.updatePage(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('SEO_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		seoService.deletePage(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
