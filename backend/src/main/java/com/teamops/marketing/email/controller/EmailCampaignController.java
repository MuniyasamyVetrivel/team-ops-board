package com.teamops.marketing.email.controller;

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
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.email.dto.EmailCampaignDtos.CampaignItem;
import com.teamops.marketing.email.dto.EmailCampaignDtos.EmailTrend;
import com.teamops.marketing.email.dto.EmailCampaignDtos.MonthlySummary;
import com.teamops.marketing.email.dto.EmailCampaignDtos.SaveCampaign;
import com.teamops.marketing.email.entity.EmailCampaignStatus;
import com.teamops.marketing.email.entity.EmailCampaignType;
import com.teamops.marketing.email.service.EmailCampaignService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Email campaigns: CAMPAIGN_VIEW reads (list, summary, trend, export), CAMPAIGN_EDIT changes. The list takes an
 * optional month/year (by campaign date); the summary and trend default to the current business month.
 */
@RestController
@RequestMapping("/api/marketing/email-campaigns")
@RequiredArgsConstructor
public class EmailCampaignController {

	static final Map<String, List<String>> SORTS = Map.of("date", List.of("campaignDate", "id"), "name",
			List.of("name", "id"), "sent", List.of("emailsSent", "id"), "leads", List.of("leadsGenerated", "id"), "status",
			List.of("status", "campaignDate"), "updated", List.of("updatedAt"));

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final EmailCampaignService campaignService;

	@GetMapping
	@PreAuthorize("hasAuthority('CAMPAIGN_VIEW')")
	public PageResponse<CampaignItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<EmailCampaignType> type,
			@RequestParam(required = false) Set<EmailCampaignStatus> status, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort) {
		return campaignService.search(search, type, status, ownerId, optionalPeriod(month, year),
				PageRequests.of(page, size, sort, SORTS, "date,desc"));
	}

	/**
	 * The month's totals and rates against {@code compareMonth}/{@code compareYear} (the previous month when omitted),
	 * plus the month per campaign type.
	 */
	@GetMapping("/summary")
	@PreAuthorize("hasAuthority('CAMPAIGN_VIEW')")
	public MonthlySummary summary(@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer compareMonth,
			@RequestParam(required = false) Integer compareYear, @RequestParam(required = false) Long ownerId) {
		MarketingPeriod period = campaignService.period(month, year);
		MarketingPeriod comparison = compareMonth == null && compareYear == null ? period.previous()
				: new MarketingPeriod(compareMonth == null ? period.month() : compareMonth,
						compareYear == null ? period.year() : compareYear);
		return campaignService.summary(period, comparison, ownerId);
	}

	/** Totals per month for {@code months} months (default 12, at most 36) ending with month/year. */
	@GetMapping("/trend")
	@PreAuthorize("hasAuthority('CAMPAIGN_VIEW')")
	public EmailTrend trend(@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer months, @RequestParam(required = false) Long ownerId) {
		return campaignService.trend(campaignService.period(month, year), months, ownerId);
	}

	@GetMapping("/export")
	@PreAuthorize("hasAuthority('CAMPAIGN_VIEW')")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<EmailCampaignType> type,
			@RequestParam(required = false) Set<EmailCampaignStatus> status, @RequestParam(required = false) Long ownerId,
			@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year) {
		MarketingPeriod period = optionalPeriod(month, year);
		String fileName = period == null ? "email-campaigns.csv"
				: "email-campaigns-%d-%02d.csv".formatted(period.year(), period.month());
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(campaignService.export(search, type, status, ownerId, period));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('CAMPAIGN_VIEW')")
	public CampaignItem get(@PathVariable Long id) {
		return campaignService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
	public ResponseEntity<CampaignItem> create(@Valid @RequestBody SaveCampaign request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
	public CampaignItem update(@PathVariable Long id, @Valid @RequestBody SaveCampaign request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return campaignService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		campaignService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	/** Month filter only when given (a year alone is not a month). */
	private MarketingPeriod optionalPeriod(Integer month, Integer year) {
		return month == null ? null : campaignService.period(month, year);
	}

}
