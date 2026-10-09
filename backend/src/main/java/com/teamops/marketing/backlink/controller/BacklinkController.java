package com.teamops.marketing.backlink.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.backlink.dto.BacklinkDtos.BacklinkItem;
import com.teamops.marketing.backlink.dto.BacklinkDtos.BacklinkSummary;
import com.teamops.marketing.backlink.dto.BacklinkDtos.BacklinkTrend;
import com.teamops.marketing.backlink.dto.BacklinkDtos.ChangeStatus;
import com.teamops.marketing.backlink.dto.BacklinkDtos.SaveBacklink;
import com.teamops.marketing.backlink.entity.BacklinkStatus;
import com.teamops.marketing.backlink.entity.BacklinkType;
import com.teamops.marketing.backlink.repository.BacklinkQuery.StageKind;
import com.teamops.marketing.backlink.service.BacklinkService;
import com.teamops.marketing.backlink.service.BacklinkService.BacklinkFilter;
import com.teamops.marketing.common.MarketingPeriod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Backlinks: BACKLINK_VIEW reads (list, detail, monthly summary, history, export), BACKLINK_EDIT changes. The list
 * takes an optional month/year with the stage it applies to ({@code stage}, any stage when omitted); the summary and
 * history default to the current business month.
 */
@RestController
@RequestMapping("/api/marketing/backlinks")
@RequiredArgsConstructor
public class BacklinkController {

	static final Map<String, List<String>> SORTS = Map.of("updated", List.of("updatedAt", "id"), "code",
			List.of("code"), "domain", List.of("referringDomain", "id"), "authority", List.of("domainAuthority", "id"),
			"submitted", List.of("submittedDate", "id"), "approved", List.of("approvedDate", "id"), "live",
			List.of("liveDate", "id"), "status", List.of("status", "updatedAt"));

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final BacklinkService backlinkService;

	@GetMapping
	@PreAuthorize("hasAuthority('BACKLINK_VIEW')")
	public PageResponse<BacklinkItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<BacklinkStatus> status,
			@RequestParam(required = false) Set<BacklinkType> linkType, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Long targetPageId, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) StageKind stage,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser viewer) {
		BacklinkFilter filter = new BacklinkFilter(search, status, linkType, ownerId, targetPageId,
				optionalPeriod(month, year), stage);
		return backlinkService.search(filter, PageRequests.of(page, size, sort, SORTS, "updated,desc"), viewer);
	}

	/**
	 * Brief section 46: the month's target, submitted, approved, live and remaining against {@code compareMonth} /
	 * {@code compareYear} (the previous month when omitted), with live links by type, activity by owner and today's
	 * pipeline.
	 */
	@GetMapping("/summary")
	@PreAuthorize("hasAuthority('BACKLINK_VIEW')")
	public BacklinkSummary summary(@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer compareMonth,
			@RequestParam(required = false) Integer compareYear, @RequestParam(required = false) Long ownerId,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		MarketingPeriod period = backlinkService.period(month, year);
		MarketingPeriod comparison = compareMonth == null && compareYear == null ? period.previous()
				: new MarketingPeriod(compareMonth == null ? period.month() : compareMonth,
						compareYear == null ? period.year() : compareYear);
		return backlinkService.summary(period, comparison, ownerId, viewer);
	}

	/** The monthly history: {@code months} months (default 12, at most 36) ending with month/year. */
	@GetMapping("/trend")
	@PreAuthorize("hasAuthority('BACKLINK_VIEW')")
	public BacklinkTrend trend(@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer months,
			@RequestParam(required = false) Long ownerId, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return backlinkService.trend(backlinkService.period(month, year), months, ownerId, viewer);
	}

	@GetMapping("/export")
	@PreAuthorize("hasAuthority('BACKLINK_VIEW')")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<BacklinkStatus> status,
			@RequestParam(required = false) Set<BacklinkType> linkType, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Long targetPageId, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) StageKind stage) {
		MarketingPeriod period = optionalPeriod(month, year);
		String fileName = period == null ? "backlinks.csv"
				: "backlinks-%d-%02d.csv".formatted(period.year(), period.month());
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(backlinkService.export(new BacklinkFilter(search, status, linkType, ownerId, targetPageId, period, stage)));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('BACKLINK_VIEW')")
	public BacklinkItem get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return backlinkService.get(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('BACKLINK_EDIT')")
	public ResponseEntity<BacklinkItem> create(@Valid @RequestBody SaveBacklink request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(backlinkService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('BACKLINK_EDIT')")
	public BacklinkItem update(@PathVariable Long id, @Valid @RequestBody SaveBacklink request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return backlinkService.update(id, request, actor, ClientInfo.from(http));
	}

	@PutMapping("/{id}/status")
	@PreAuthorize("hasAuthority('BACKLINK_EDIT')")
	public BacklinkItem changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatus request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return backlinkService.changeStatus(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('BACKLINK_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		backlinkService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	/** Month filter only when given (a year alone is not a month). */
	private MarketingPeriod optionalPeriod(Integer month, Integer year) {
		return month == null ? null : backlinkService.period(month, year);
	}

}
