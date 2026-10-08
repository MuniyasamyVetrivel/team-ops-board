package com.teamops.marketing.lead.controller;

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
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.lead.dto.LeadDtos.ChangeStatus;
import com.teamops.marketing.lead.dto.LeadDtos.LeadItem;
import com.teamops.marketing.lead.dto.LeadDtos.LeadSummary;
import com.teamops.marketing.lead.dto.LeadDtos.LeadTrend;
import com.teamops.marketing.lead.dto.LeadDtos.LinkOption;
import com.teamops.marketing.lead.dto.LeadDtos.SaveLead;
import com.teamops.marketing.lead.entity.LeadStatus;
import com.teamops.marketing.lead.repository.LeadQuery.LinkKind;
import com.teamops.marketing.lead.service.LeadService;
import com.teamops.marketing.lead.service.LeadService.LeadFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Marketing leads: LEAD_VIEW reads (list, detail, summary, trend, link options, export), LEAD_EDIT changes. The list
 * takes an optional month/year (by lead date); the summary and trend default to the current business month.
 */
@RestController
@RequestMapping("/api/marketing/leads")
@RequiredArgsConstructor
public class LeadController {

	static final Map<String, List<String>> SORTS = Map.of("date", List.of("leadDate", "id"), "name",
			List.of("name", "id"), "company", List.of("company", "id"), "status", List.of("status", "leadDate"), "source",
			List.of("source", "leadDate"), "code", List.of("code"), "updated", List.of("updatedAt"));

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final LeadService leadService;

	@GetMapping
	@PreAuthorize("hasAuthority('LEAD_VIEW')")
	public PageResponse<LeadItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<LeadSource> source, @RequestParam(required = false) Set<LeadStatus> status,
			@RequestParam(required = false) Long ownerId, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Long emailCampaignId,
			@RequestParam(required = false) Long paidCampaignId, @RequestParam(required = false) Long contentItemId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser viewer) {
		LeadFilter filter = new LeadFilter(search, source, status, ownerId, optionalPeriod(month, year), emailCampaignId,
				paidCampaignId, contentItemId);
		return leadService.search(filter, PageRequests.of(page, size, sort, SORTS, "date,desc"), viewer);
	}

	/**
	 * The month's leads by source (with their targets for TARGET_VIEW holders) against {@code compareMonth} /
	 * {@code compareYear} (the previous month when omitted), by status, and the top campaigns and content.
	 */
	@GetMapping("/summary")
	@PreAuthorize("hasAuthority('LEAD_VIEW')")
	public LeadSummary summary(@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer compareMonth, @RequestParam(required = false) Integer compareYear,
			@RequestParam(required = false) Long ownerId, @AuthenticationPrincipal AuthenticatedUser viewer) {
		MarketingPeriod period = leadService.period(month, year);
		MarketingPeriod comparison = compareMonth == null && compareYear == null ? period.previous()
				: new MarketingPeriod(compareMonth == null ? period.month() : compareMonth,
						compareYear == null ? period.year() : compareYear);
		return leadService.summary(period, comparison, ownerId, viewer);
	}

	/** Leads per month and source for {@code months} months (default 12, at most 36) ending with month/year. */
	@GetMapping("/trend")
	@PreAuthorize("hasAuthority('LEAD_VIEW')")
	public LeadTrend trend(@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer months, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) LeadSource source) {
		return leadService.trend(leadService.period(month, year), months, ownerId, source);
	}

	/** Campaigns or content a lead can name: sent email campaigns, started paid campaigns, published content. */
	@GetMapping("/link-options")
	@PreAuthorize("hasAuthority('LEAD_VIEW')")
	public List<LinkOption> linkOptions(@RequestParam LinkKind kind, @RequestParam(required = false) String search) {
		return leadService.linkOptions(kind, search);
	}

	@GetMapping("/export")
	@PreAuthorize("hasAuthority('LEAD_VIEW')")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<LeadSource> source, @RequestParam(required = false) Set<LeadStatus> status,
			@RequestParam(required = false) Long ownerId, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Long emailCampaignId,
			@RequestParam(required = false) Long paidCampaignId, @RequestParam(required = false) Long contentItemId) {
		MarketingPeriod period = optionalPeriod(month, year);
		String fileName = period == null ? "marketing-leads.csv"
				: "marketing-leads-%d-%02d.csv".formatted(period.year(), period.month());
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(leadService.export(new LeadFilter(search, source, status, ownerId, period, emailCampaignId,
					paidCampaignId, contentItemId)));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('LEAD_VIEW')")
	public LeadItem get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return leadService.get(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('LEAD_EDIT')")
	public ResponseEntity<LeadItem> create(@Valid @RequestBody SaveLead request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(leadService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('LEAD_EDIT')")
	public LeadItem update(@PathVariable Long id, @Valid @RequestBody SaveLead request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return leadService.update(id, request, actor, ClientInfo.from(http));
	}

	@PutMapping("/{id}/status")
	@PreAuthorize("hasAuthority('LEAD_EDIT')")
	public LeadItem changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatus request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return leadService.changeStatus(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('LEAD_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		leadService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	/** Month filter only when given (a year alone is not a month). */
	private MarketingPeriod optionalPeriod(Integer month, Integer year) {
		return month == null ? null : leadService.period(month, year);
	}

}
