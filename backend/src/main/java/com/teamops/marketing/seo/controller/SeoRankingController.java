package com.teamops.marketing.seo.controller;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.RankingChange.Movement;
import com.teamops.marketing.seo.dto.RankingDtos.CorrectRanking;
import com.teamops.marketing.seo.dto.RankingDtos.KeywordHistory;
import com.teamops.marketing.seo.dto.RankingDtos.MonthlyReport;
import com.teamops.marketing.seo.dto.RankingDtos.PageHistory;
import com.teamops.marketing.seo.dto.RankingDtos.RecordMonthly;
import com.teamops.marketing.seo.dto.RankingDtos.RecordRanking;
import com.teamops.marketing.seo.dto.RankingDtos.RecordResult;
import com.teamops.marketing.seo.dto.SeoDtos.KeywordItem;
import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.repository.SeoRankingTableQuery.Filter;
import com.teamops.marketing.seo.repository.SeoRankingTableQuery.StandingFilter;
import com.teamops.marketing.seo.service.KeywordRankingService;
import com.teamops.marketing.seo.service.SeoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * Monthly keyword rankings: the SEO ranking table and its CSV export, the monthly report, recording (single and the
 * monthly update), corrections, and keyword and page history. Months default to the current business month.
 */
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class SeoRankingController {

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final KeywordRankingService rankingService;

	private final SeoService seoService;

	/**
	 * Sorts: best, worst, improvement, decline (fixed order), and keyword, page, volume, updated (with ,asc/,desc).
	 * {@code standing} filters on the month's status; NOT_RANKED includes months without a ranking.
	 */
	@GetMapping("/rankings")
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public PageResponse<KeywordItem> table(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long pageId, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Set<KeywordStatus> status, @RequestParam(required = false) Device device,
			@RequestParam(required = false) StandingFilter standing,
			@RequestParam(required = false) @Min(1) @Max(100) Integer minPosition,
			@RequestParam(required = false) @Min(1) @Max(100) Integer maxPosition,
			@RequestParam(required = false) Movement movement, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort) {
		Filter filter = new Filter(search, pageId, ownerId, status, device, standing, minPosition, maxPosition,
				movement);
		return rankingService.table(filter, seoService.period(month, year), sort, page, size);
	}

	/** The ranking table as a CSV download, with the same filters and order. */
	@GetMapping("/rankings/export")
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long pageId, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Set<KeywordStatus> status, @RequestParam(required = false) Device device,
			@RequestParam(required = false) StandingFilter standing,
			@RequestParam(required = false) @Min(1) @Max(100) Integer minPosition,
			@RequestParam(required = false) @Min(1) @Max(100) Integer maxPosition,
			@RequestParam(required = false) Movement movement, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) String sort) {
		MarketingPeriod period = seoService.period(month, year);
		Filter filter = new Filter(search, pageId, ownerId, status, device, standing, minPosition, maxPosition,
				movement);
		String fileName = "seo-rankings-%d-%02d.csv".formatted(period.year(), period.month());
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(rankingService.export(filter, period, sort));
	}

	@GetMapping("/rankings/monthly")
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public MonthlyReport monthly(@RequestParam(required = false) Long pageId,
			@RequestParam(required = false) Long ownerId, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year) {
		return rankingService.monthlyReport(pageId, ownerId, seoService.period(month, year));
	}

	/** The monthly update: records many keywords for one month, all or nothing (409 if any is already recorded). */
	@PostMapping("/rankings/monthly")
	@PreAuthorize("hasAuthority('SEO_EDIT')")
	public ResponseEntity<RecordResult> recordMonthly(@Valid @RequestBody RecordMonthly request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(rankingService.recordMonthly(request, actor, ClientInfo.from(http)));
	}

	/** Corrects a ranking (audited). Closed months return 409 MONTH_LOCKED, except for a Super Admin. */
	@PutMapping("/rankings/{id}")
	@PreAuthorize("hasAuthority('SEO_EDIT')")
	public KeywordHistory correct(@PathVariable Long id, @Valid @RequestBody CorrectRanking request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return rankingService.correct(id, request, actor, ClientInfo.from(http));
	}

	@GetMapping("/keywords/{id}/rankings")
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public KeywordHistory history(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return rankingService.history(id, viewer);
	}

	/** Records a month that has no ranking yet (409 RANKING_EXISTS otherwise). */
	@PostMapping("/keywords/{id}/rankings")
	@PreAuthorize("hasAuthority('SEO_EDIT')")
	public ResponseEntity<KeywordHistory> record(@PathVariable Long id, @Valid @RequestBody RecordRanking request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(rankingService.record(id, request, actor, ClientInfo.from(http)));
	}

	/** Positions of the page's keywords for {@code months} months (default 12, at most 24) ending at month/year. */
	@GetMapping("/pages/{id}/rankings")
	@PreAuthorize("hasAuthority('SEO_VIEW')")
	public PageHistory pageHistory(@PathVariable Long id, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer months) {
		return rankingService.pageHistory(id, seoService.period(month, year), months);
	}

}
