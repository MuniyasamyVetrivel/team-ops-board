package com.teamops.marketing.email.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.email.dto.EmailCampaignDtos.CampaignItem;
import com.teamops.marketing.email.dto.EmailCampaignDtos.EmailTrend;
import com.teamops.marketing.email.dto.EmailCampaignDtos.MonthTotals;
import com.teamops.marketing.email.dto.EmailCampaignDtos.MonthlySummary;
import com.teamops.marketing.email.dto.EmailCampaignDtos.SaveCampaign;
import com.teamops.marketing.email.dto.EmailCampaignDtos.TypeTotals;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.entity.EmailCampaignStatus;
import com.teamops.marketing.email.entity.EmailCampaignType;
import com.teamops.marketing.email.repository.EmailCampaignQuery;
import com.teamops.marketing.email.repository.EmailCampaignQuery.Totals;
import com.teamops.marketing.email.repository.EmailCampaignRepository;
import com.teamops.marketing.lead.repository.MarketingLeadRepository;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Email campaigns (brief sections 37–39): CAMPAIGN_VIEW reads, CAMPAIGN_EDIT changes. Only raw counts are stored;
 * rates come from {@link EmailRates}. Counts belong to SENT campaigns, which count in the month of their campaign date.
 * A sent campaign is history: it can be corrected but not deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailCampaignService {

	public static final int EXPORT_LIMIT = 5_000;

	public static final int DEFAULT_TREND_MONTHS = 12;

	public static final int MAX_TREND_MONTHS = 36;

	private static final String ENTITY = "EMAIL_CAMPAIGN";

	private final EmailCampaignRepository campaignRepository;

	private final EmailCampaignQuery campaignQuery;

	private final MarketingLeadRepository leadRepository;

	private final UserRepository userRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	public MarketingPeriod period(Integer month, Integer year) {
		return MarketingPeriod.resolve(month, year, calendar.today());
	}

	// --- reading ----------------------------------------------------------------------------------------------

	/** Campaigns, optionally limited to one month (by campaign date). */
	public PageResponse<CampaignItem> search(String search, Set<EmailCampaignType> types,
			Set<EmailCampaignStatus> statuses, Long ownerId, MarketingPeriod period, Pageable pageable) {
		return PageResponse.of(campaignRepository.findAll(spec(search, types, statuses, ownerId, period), pageable)
			.map(CampaignItem::of));
	}

	public CampaignItem get(Long id) {
		return CampaignItem.of(load(id));
	}

	/** The month's totals and rates, the comparison month's, and the month per campaign type. */
	public MonthlySummary summary(MarketingPeriod period, MarketingPeriod comparison, Long ownerId) {
		Map<EmailCampaignType, Totals> byType = campaignQuery.byType(period, ownerId);
		List<TypeTotals> types = new ArrayList<>();
		byType.forEach((type, totals) -> types.add(new TypeTotals(type, totals.campaigns(), totals.counts(),
				EmailRates.of(totals.counts()))));
		return new MonthlySummary(monthTotals(period, ownerId), monthTotals(comparison, ownerId), types);
	}

	/** Totals per month for {@code months} months ending with {@code end}, oldest first. */
	public EmailTrend trend(MarketingPeriod end, Integer months, Long ownerId) {
		int count = months == null ? DEFAULT_TREND_MONTHS : Math.clamp(months, 1, MAX_TREND_MONTHS);
		MarketingPeriod start = end.plusMonths(-(count - 1));
		Map<MarketingPeriod, Totals> totals = campaignQuery.monthly(start, end, ownerId);
		List<MonthTotals> points = new ArrayList<>();
		for (MarketingPeriod p = start; !p.firstDay().isAfter(end.firstDay()); p = p.next()) {
			points.add(toMonth(p, totals.getOrDefault(p, Totals.EMPTY)));
		}
		return new EmailTrend(points);
	}

	/** The campaign table as CSV (same filters, newest first), at most {@link #EXPORT_LIMIT} rows. */
	public byte[] export(String search, Set<EmailCampaignType> types, Set<EmailCampaignStatus> statuses, Long ownerId,
			MarketingPeriod period) {
		List<EmailCampaign> campaigns = campaignRepository
			.findAll(spec(search, types, statuses, ownerId, period),
					PageRequest.of(0, EXPORT_LIMIT,
							Sort.by(Sort.Order.desc("campaignDate"), Sort.Order.desc("id"))))
			.getContent();
		List<List<String>> rows = campaigns.stream().map(c -> {
			EmailCounts n = c.counts();
			EmailRates r = EmailRates.of(n);
			return List.of(c.getName(), c.getCampaignType().name(), c.getCampaignDate().toString(), c.getStatus().name(),
					c.getOwner() == null ? "" : c.getOwner().getFullName(), nullToEmpty(c.getAudience()),
					String.valueOf(n.emailsSent()), String.valueOf(n.delivered()), String.valueOf(n.bounced()),
					String.valueOf(n.opened()), String.valueOf(n.uniqueOpens()), String.valueOf(n.clicked()),
					String.valueOf(n.uniqueClicks()), String.valueOf(n.unsubscribed()), String.valueOf(n.leads()),
					rate(r.deliveryRate()), rate(r.openRate()), rate(r.clickRate()), rate(r.clickToOpenRate()),
					rate(r.leadConversionRate()), nullToEmpty(c.getExternalId()));
		}).toList();
		return CsvWriter.write(List.of("Name", "Type", "Campaign date", "Status", "Owner", "Audience", "Emails sent",
				"Delivered", "Bounced", "Opened", "Unique opens", "Clicked", "Unique clicks", "Unsubscribed", "Leads",
				"Delivery rate %", "Open rate %", "Click rate %", "Click-to-open rate %", "Lead conversion %",
				"External ID"), rows);
	}

	// --- changes ----------------------------------------------------------------------------------------------

	@Transactional
	public CampaignItem create(SaveCampaign request, AuthenticatedUser actor, ClientInfo client) {
		EmailCounts counts = request.counts();
		validate(request.status(), request.campaignDate(), counts);
		EmailCampaign campaign = new EmailCampaign();
		campaign.setName(request.name().trim());
		campaign.setCampaignType(request.campaignType());
		campaign.setCampaignDate(request.campaignDate());
		campaign.setOwner(resolveOwner(request.ownerId()));
		campaign.setAudience(trimToNull(request.audience()));
		campaign.setStatus(request.status());
		campaign.apply(counts);
		campaign.setNotes(trimToNull(request.notes()));
		EmailCampaign saved = campaignRepository.save(campaign);
		auditService.record(AuditAction.EMAIL_CAMPAIGN_CREATED, actor.id(), ENTITY, saved.getId(),
				Map.of("name", saved.getName(), "status", saved.getStatus(), "campaignDate", saved.getCampaignDate().toString()),
				client);
		return CampaignItem.of(saved);
	}

	@Transactional
	public CampaignItem update(Long id, SaveCampaign request, AuthenticatedUser actor, ClientInfo client) {
		EmailCampaign campaign = load(id);
		if (request.version() == null || !Objects.equals(campaign.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this campaign just now. Reload and try again.");
		}
		EmailCounts counts = request.counts();
		validate(request.status(), request.campaignDate(), counts);
		requireLeadsStillFit(id, request.status(), request.campaignDate());
		User owner = Objects.equals(userId(campaign.getOwner()), request.ownerId()) ? campaign.getOwner()
				: resolveOwner(request.ownerId());
		AuditChanges changes = new AuditChanges().track("name", campaign.getName(), request.name().trim())
			.track("campaignType", campaign.getCampaignType(), request.campaignType())
			.track("campaignDate", campaign.getCampaignDate(), request.campaignDate())
			.track("status", campaign.getStatus(), request.status())
			.track("ownerId", userId(campaign.getOwner()), userId(owner))
			.track("counts", campaign.counts(), counts);
		campaign.setName(request.name().trim());
		campaign.setCampaignType(request.campaignType());
		campaign.setCampaignDate(request.campaignDate());
		campaign.setOwner(owner);
		campaign.setAudience(trimToNull(request.audience()));
		campaign.setStatus(request.status());
		campaign.apply(counts);
		campaign.setNotes(trimToNull(request.notes()));
		campaignRepository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.EMAIL_CAMPAIGN_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), client);
		}
		return CampaignItem.of(campaign);
	}

	/** Drafts, scheduled and cancelled campaigns can be deleted; a sent campaign is history. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		EmailCampaign campaign = load(id);
		if (campaign.getStatus() == EmailCampaignStatus.SENT) {
			throw ApiException.conflict("CAMPAIGN_SENT", "A sent campaign is part of the monthly figures and cannot be deleted");
		}
		if (leadRepository.existsByEmailCampaignId(id)) {
			throw ApiException.conflict("CAMPAIGN_HAS_LEADS", "Leads name this campaign, so it cannot be deleted");
		}
		campaignRepository.delete(campaign);
		auditService.record(AuditAction.EMAIL_CAMPAIGN_DELETED, actor.id(), ENTITY, id, Map.of("name", campaign.getName()),
				client);
	}

	// --- shared with the CSV importer -------------------------------------------------------------------------

	/** Counts make sense together, belong only to sent campaigns, and a sent campaign is not dated in the future. */
	void validate(EmailCampaignStatus status, LocalDate campaignDate, EmailCounts counts) {
		List<EmailCounts.Problem> problems = counts.problems();
		if (!problems.isEmpty()) {
			EmailCounts.Problem first = problems.getFirst();
			throw ApiException.badRequest("INVALID_COUNTS", label(first.field()) + ": " + first.message().toLowerCase(Locale.ROOT));
		}
		if (status != EmailCampaignStatus.SENT && !counts.isZero()) {
			throw ApiException.badRequest("COUNTS_NEED_SENT", "Counts can only be recorded for a sent campaign");
		}
		if (status == EmailCampaignStatus.SENT && campaignDate.isAfter(calendar.today())) {
			throw ApiException.badRequest("FUTURE_SEND_DATE", "A sent campaign cannot be dated in the future");
		}
	}

	/** Leads that name this campaign need it to stay sent, dated no later than the first of them. */
	private void requireLeadsStillFit(Long id, EmailCampaignStatus status, LocalDate campaignDate) {
		LocalDate firstLead = leadRepository.findFirstLeadDateForEmailCampaign(id);
		if (firstLead == null) {
			return;
		}
		if (status != EmailCampaignStatus.SENT) {
			throw ApiException.conflict("CAMPAIGN_HAS_LEADS", "Leads name this campaign, so it must stay sent");
		}
		if (campaignDate.isAfter(firstLead)) {
			throw ApiException.conflict("CAMPAIGN_HAS_LEADS",
					"Leads name this campaign from " + firstLead + "; it cannot be dated after that");
		}
	}

	/** Owners must be active Digital Marketing users. */
	User resolveOwner(Long ownerId) {
		if (ownerId == null) {
			return null;
		}
		return userRepository.findActiveWithPermission(MarketingContextService.MARKETING_VIEW, UserStatus.ACTIVE)
			.stream()
			.filter(user -> user.getId().equals(ownerId))
			.findFirst()
			.orElseThrow(() -> ApiException.badRequest("INVALID_OWNER",
					"The owner must be an active user with access to Digital Marketing"));
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private MonthTotals monthTotals(MarketingPeriod period, Long ownerId) {
		return toMonth(period, campaignQuery.monthly(period, period, ownerId).getOrDefault(period, Totals.EMPTY));
	}

	private static MonthTotals toMonth(MarketingPeriod period, Totals totals) {
		return new MonthTotals(Period.of(period), totals.campaigns(), totals.counts(), EmailRates.of(totals.counts()));
	}

	private static Specification<EmailCampaign> spec(String search, Set<EmailCampaignType> types,
			Set<EmailCampaignStatus> statuses, Long ownerId, MarketingPeriod period) {
		Specification<EmailCampaign> spec = (root, query, cb) -> cb.conjunction();
		if (StringUtils.hasText(search)) {
			String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
				.replace("_", "\\_") + "%";
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
					cb.like(cb.lower(root.get("audience")), pattern, '\\')));
		}
		if (types != null && !types.isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("campaignType").in(types));
		}
		if (statuses != null && !statuses.isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("status").in(statuses));
		}
		if (ownerId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId));
		}
		if (period != null) {
			spec = spec.and((root, query, cb) -> cb.between(root.get("campaignDate"), period.firstDay(), period.lastDay()));
		}
		return spec;
	}

	private EmailCampaign load(Long id) {
		return campaignRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("EMAIL_CAMPAIGN_NOT_FOUND", "Campaign not found"));
	}

	/** "uniqueOpens" → "Unique opens". */
	static String label(String field) {
		String words = field.replaceAll("([A-Z])", " $1").toLowerCase(Locale.ROOT);
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	private static String rate(BigDecimal value) {
		return value == null ? "" : value.toPlainString();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static Long userId(User user) {
		return user == null ? null : user.getId();
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
