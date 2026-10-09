package com.teamops.marketing.content.controller;

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
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.content.dto.ContentDtos.Attachment;
import com.teamops.marketing.content.dto.ContentDtos.ChangeStatus;
import com.teamops.marketing.content.dto.ContentDtos.ContentItemDto;
import com.teamops.marketing.content.dto.ContentDtos.ContentSummary;
import com.teamops.marketing.content.dto.ContentDtos.ContentTrend;
import com.teamops.marketing.content.dto.ContentDtos.SaveContent;
import com.teamops.marketing.content.entity.ContentStatus;
import com.teamops.marketing.content.entity.ContentType;
import com.teamops.marketing.content.service.ContentService;
import com.teamops.marketing.content.service.ContentService.ContentFilter;
import com.teamops.marketing.content.service.ContentService.DateField;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Content & Blog: CONTENT_VIEW reads (list, detail, monthly summary, history, export), CONTENT_EDIT changes. The list
 * takes an optional month/year with the date it applies to ({@code dateField}: PLANNED or PUBLISHED; planned,
 * published or refreshed when omitted); the summary and history default to the current business month.
 */
@RestController
@RequestMapping("/api/marketing/content")
@RequiredArgsConstructor
public class ContentController {

	static final Map<String, List<String>> SORTS = Map.of("updated", List.of("updatedAt", "id"), "title",
			List.of("title", "id"), "published", List.of("publicationDate", "id"), "planned", List.of("plannedDate", "id"),
			"traffic", List.of("organicTraffic", "id"), "cta", List.of("ctaClicks", "id"), "status",
			List.of("status", "updatedAt"));

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final ContentService contentService;

	@GetMapping
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	public PageResponse<ContentItemDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<ContentStatus> status,
			@RequestParam(required = false) Set<ContentType> contentType, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Long authorId, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) DateField dateField,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser viewer) {
		ContentFilter filter = new ContentFilter(search, status, contentType, ownerId, authorId,
				optionalPeriod(month, year), dateField);
		return contentService.search(filter, PageRequests.of(page, size, sort, SORTS, "updated,desc"), viewer);
	}

	/**
	 * Brief section 48: the month's blog target, blogs planned and published, remaining and leads against
	 * {@code compareMonth} / {@code compareYear} (the previous month when omitted), with content published by type,
	 * today's pipeline and the content that brought in the most leads.
	 */
	@GetMapping("/summary")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	public ContentSummary summary(@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer compareMonth,
			@RequestParam(required = false) Integer compareYear, @RequestParam(required = false) Long ownerId,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		MarketingPeriod period = contentService.period(month, year);
		MarketingPeriod comparison = compareMonth == null && compareYear == null ? period.previous()
				: new MarketingPeriod(compareMonth == null ? period.month() : compareMonth,
						compareYear == null ? period.year() : compareYear);
		return contentService.summary(period, comparison, ownerId, viewer);
	}

	/** The monthly history: {@code months} months (default 12, at most 36) ending with month/year. */
	@GetMapping("/trend")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	public ContentTrend trend(@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer months, @RequestParam(required = false) Long ownerId,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return contentService.trend(contentService.period(month, year), months, ownerId, viewer);
	}

	@GetMapping("/export")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<ContentStatus> status,
			@RequestParam(required = false) Set<ContentType> contentType, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Long authorId, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) DateField dateField) {
		MarketingPeriod period = optionalPeriod(month, year);
		String fileName = period == null ? "content.csv" : "content-%d-%02d.csv".formatted(period.year(), period.month());
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(contentService.export(new ContentFilter(search, status, contentType, ownerId, authorId, period, dateField)));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	public ContentItemDto get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return contentService.get(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('CONTENT_EDIT')")
	public ResponseEntity<ContentItemDto> create(@Valid @RequestBody SaveContent request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(contentService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('CONTENT_EDIT')")
	public ContentItemDto update(@PathVariable Long id, @Valid @RequestBody SaveContent request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return contentService.update(id, request, actor, ClientInfo.from(http));
	}

	@PutMapping("/{id}/status")
	@PreAuthorize("hasAuthority('CONTENT_EDIT')")
	public ContentItemDto changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatus request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return contentService.changeStatus(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('CONTENT_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		contentService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/attachments")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	public List<Attachment> attachments(@PathVariable Long id) {
		return contentService.attachments(id);
	}

	@PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('CONTENT_EDIT')")
	public List<Attachment> addAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return contentService.addAttachment(id, file, actor);
	}

	/** Always served as a download. */
	@GetMapping("/{id}/attachments/{fileId}")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId) {
		ContentService.Download download = contentService.download(id, fileId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.contentType()))
			.contentLength(download.sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(download.content());
	}

	@DeleteMapping("/{id}/attachments/{fileId}")
	@PreAuthorize("hasAuthority('CONTENT_EDIT')")
	public List<Attachment> deleteAttachment(@PathVariable Long id, @PathVariable Long fileId) {
		return contentService.deleteAttachment(id, fileId);
	}

	/** Month filter only when given (a year alone is not a month). */
	private MarketingPeriod optionalPeriod(Integer month, Integer year) {
		return month == null ? null : contentService.period(month, year);
	}

}
