package com.teamops.marketing.paid.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
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
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingMonths;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.lead.repository.MarketingLeadRepository;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.CampaignDetail;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.CampaignItem;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.CampaignPermissions;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.Figures;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.MonthRow;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.MonthTotals;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.MonthlySummary;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.PaidTrend;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.PlatformTotals;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.RunningBudget;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.SaveCampaign;
import com.teamops.marketing.paid.dto.PaidCampaignDtos.SaveMonth;
import com.teamops.marketing.paid.entity.AdDataSource;
import com.teamops.marketing.paid.entity.AdPlatform;
import com.teamops.marketing.paid.entity.PaidCampaign;
import com.teamops.marketing.paid.entity.PaidCampaignMonth;
import com.teamops.marketing.paid.entity.PaidCampaignStatus;
import com.teamops.marketing.paid.repository.PaidCampaignMonthRepository;
import com.teamops.marketing.paid.repository.PaidCampaignQuery;
import com.teamops.marketing.paid.repository.PaidCampaignQuery.Totals;
import com.teamops.marketing.paid.repository.PaidCampaignRepository;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Paid campaigns (brief sections 40–42): CAMPAIGN_VIEW reads, CAMPAIGN_EDIT changes. A campaign is the plan (dates,
 * budget); its results are recorded per month, inside the campaign's dates and never for a future month. A month is
 * added once and then corrected (audited) while it is open; a Super Admin may correct any past month
 * ({@link MarketingMonths}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaidCampaignService {

	public static final String CAMPAIGN_EDIT = "CAMPAIGN_EDIT";

	public static final int EXPORT_LIMIT = 5_000;

	public static final int DEFAULT_TREND_MONTHS = 12;

	public static final int MAX_TREND_MONTHS = 36;

	private static final String ENTITY = "PAID_CAMPAIGN";

	private final PaidCampaignRepository campaignRepository;

	private final PaidCampaignMonthRepository monthRepository;

	private final PaidCampaignQuery campaignQuery;

	private final MarketingLeadRepository leadRepository;

	private final UserRepository userRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	public MarketingPeriod period(Integer month, Integer year) {
		return MarketingPeriod.resolve(month, year, calendar.today());
	}

	// --- reading ----------------------------------------------------------------------------------------------

	/** Campaigns, optionally only those running in a month (their figures for that month are then included). */
	public PageResponse<CampaignItem> search(String search, Set<AdPlatform> platforms, Set<PaidCampaignStatus> statuses,
			Long ownerId, MarketingPeriod period, Pageable pageable) {
		Page<PaidCampaign> page = campaignRepository.findAll(spec(search, platforms, statuses, ownerId, period), pageable);
		List<Long> ids = page.getContent().stream().map(PaidCampaign::getId).toList();
		Map<Long, PaidResults> lifetime = campaignQuery.byCampaign(ids, null);
		Map<Long, PaidResults> month = period == null ? Map.of() : campaignQuery.byCampaign(ids, period);
		return PageResponse.of(page.map(c -> toItem(c, lifetime.getOrDefault(c.getId(), PaidResults.ZERO),
				period == null ? null : month.getOrDefault(c.getId(), PaidResults.ZERO))));
	}

	public CampaignDetail get(Long id, AuthenticatedUser viewer) {
		return toDetail(load(id), viewer);
	}

	/** The month across campaigns against a comparison month, the month per platform, and the running budget. */
	public MonthlySummary summary(MarketingPeriod period, MarketingPeriod comparison, Long ownerId, AdPlatform platform) {
		List<PlatformTotals> platforms = new ArrayList<>();
		campaignQuery.byPlatform(period, ownerId)
			.forEach((p, totals) -> platforms.add(new PlatformTotals(p, totals.campaigns(), Figures.of(totals.results()))));
		PaidCampaignQuery.Budget running = campaignQuery.running(period, ownerId, platform);
		return new MonthlySummary(monthTotals(period, ownerId, platform), monthTotals(comparison, ownerId, platform), platforms,
				new RunningBudget(running.campaigns(), BudgetProgress.of(running.budget(), running.spent())));
	}

	/** Spend, leads and rates per month for {@code months} months ending with {@code end}, oldest first. */
	public PaidTrend trend(MarketingPeriod end, Integer months, Long ownerId, AdPlatform platform) {
		int count = months == null ? DEFAULT_TREND_MONTHS : Math.clamp(months, 1, MAX_TREND_MONTHS);
		MarketingPeriod start = end.plusMonths(-(count - 1));
		Map<MarketingPeriod, Totals> totals = campaignQuery.monthly(start, end, ownerId, platform);
		List<MonthTotals> points = new ArrayList<>();
		for (MarketingPeriod p = start; !p.firstDay().isAfter(end.firstDay()); p = p.next()) {
			Totals t = totals.getOrDefault(p, Totals.EMPTY);
			points.add(new MonthTotals(Period.of(p), t.campaigns(), Figures.of(t.results())));
		}
		return new PaidTrend(points);
	}

	/** The campaign table as CSV (same filters), with lifetime figures, or the month's when a month is given. */
	public byte[] export(String search, Set<AdPlatform> platforms, Set<PaidCampaignStatus> statuses, Long ownerId,
			MarketingPeriod period) {
		List<PaidCampaign> campaigns = campaignRepository.findAll(spec(search, platforms, statuses, ownerId, period),
				PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("id")))).getContent();
		List<Long> ids = campaigns.stream().map(PaidCampaign::getId).toList();
		Map<Long, PaidResults> lifetime = campaignQuery.byCampaign(ids, null);
		Map<Long, PaidResults> shown = period == null ? lifetime : campaignQuery.byCampaign(ids, period);
		List<List<String>> rows = campaigns.stream().map(c -> {
			PaidResults r = shown.getOrDefault(c.getId(), PaidResults.ZERO);
			PaidRates rates = PaidRates.of(r);
			BudgetProgress budget = BudgetProgress.of(c.getBudget(), lifetime.getOrDefault(c.getId(), PaidResults.ZERO).spend());
			return List.of(c.getName(), c.getPlatform().name(), c.getObjective().name(), c.getStatus().name(),
					c.getStartDate().toString(), c.getEndDate() == null ? "" : c.getEndDate().toString(),
					c.getOwner() == null ? "" : c.getOwner().getFullName(), plain(c.getBudget()), plain(budget.spent()),
					plain(budget.remaining()), plain(r.spend()), String.valueOf(r.impressions()), String.valueOf(r.clicks()),
					String.valueOf(r.leads()), String.valueOf(r.conversions()), plain(rates.ctr()), plain(rates.costPerLead()),
					plain(rates.conversionRate()), c.getCurrency());
		}).toList();
		return CsvWriter.write(List.of("Name", "Platform", "Objective", "Status", "Start date", "End date", "Owner",
				"Budget", "Spent to date", "Remaining budget", period == null ? "Spend" : "Spend (" + period.label() + ")",
				"Impressions", "Clicks", "Leads", "Conversions", "CTR %", "Cost per lead", "Conversion rate %", "Currency"),
				rows);
	}

	// --- campaigns --------------------------------------------------------------------------------------------

	@Transactional
	public CampaignDetail create(SaveCampaign request, AuthenticatedUser actor, ClientInfo client) {
		validateDates(request.startDate(), request.endDate());
		PaidCampaign campaign = new PaidCampaign();
		applyPlan(campaign, request, resolveOwner(request.ownerId()));
		PaidCampaign saved = campaignRepository.save(campaign);
		auditService.record(AuditAction.PAID_CAMPAIGN_CREATED, actor.id(), ENTITY, saved.getId(),
				Map.of("name", saved.getName(), "platform", saved.getPlatform(), "budget", saved.getBudget()), client);
		return toDetail(saved, actor);
	}

	@Transactional
	public CampaignDetail update(Long id, SaveCampaign request, AuthenticatedUser actor, ClientInfo client) {
		PaidCampaign campaign = load(id);
		if (request.version() == null || !Objects.equals(campaign.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this campaign just now. Reload and try again.");
		}
		validateDates(request.startDate(), request.endDate());
		boolean hasResults = monthRepository.existsByCampaignId(id);
		if (hasResults && request.status() == PaidCampaignStatus.DRAFT) {
			throw ApiException.conflict("CAMPAIGN_HAS_RESULTS", "A campaign with recorded results cannot go back to draft");
		}
		if (hasResults) {
			requireDatesCoverResults(id, request.startDate(), request.endDate());
		}
		LocalDate firstLead = leadRepository.findFirstLeadDateForPaidCampaign(id);
		if (firstLead != null && request.status() == PaidCampaignStatus.DRAFT) {
			throw ApiException.conflict("CAMPAIGN_HAS_LEADS", "Leads name this campaign, so it cannot go back to draft");
		}
		if (firstLead != null && request.startDate().isAfter(firstLead)) {
			throw ApiException.conflict("CAMPAIGN_HAS_LEADS",
					"Leads name this campaign from " + firstLead + "; it cannot start after that");
		}
		if (firstLead != null && campaign.getPlatform() == AdPlatform.LINKEDIN && platformOf(request) != AdPlatform.LINKEDIN
				&& leadRepository.existsByPaidCampaignIdAndSource(id, LeadSource.LINKEDIN)) {
			throw ApiException.conflict("CAMPAIGN_HAS_LEADS", "LinkedIn leads name this campaign, so it stays on LinkedIn");
		}
		User owner = Objects.equals(userId(campaign.getOwner()), request.ownerId()) ? campaign.getOwner()
				: resolveOwner(request.ownerId());
		AuditChanges changes = new AuditChanges().track("name", campaign.getName(), request.name().trim())
			.track("platform", campaign.getPlatform(), platformOf(request))
			.track("objective", campaign.getObjective(), request.objective())
			.track("startDate", campaign.getStartDate(), request.startDate())
			.track("endDate", campaign.getEndDate(), request.endDate())
			.track("budget", campaign.getBudget(), money(request.budget()))
			.track("status", campaign.getStatus(), request.status())
			.track("ownerId", userId(campaign.getOwner()), userId(owner));
		applyPlan(campaign, request, owner);
		campaignRepository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.PAID_CAMPAIGN_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), client);
		}
		return toDetail(campaign, actor);
	}

	/** Only a campaign without results can be deleted; its months are history. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		PaidCampaign campaign = load(id);
		if (monthRepository.existsByCampaignId(id)) {
			throw ApiException.conflict("CAMPAIGN_HAS_RESULTS", "This campaign has recorded results and cannot be deleted");
		}
		if (leadRepository.existsByPaidCampaignId(id)) {
			throw ApiException.conflict("CAMPAIGN_HAS_LEADS", "Leads name this campaign, so it cannot be deleted");
		}
		campaignRepository.delete(campaign);
		auditService.record(AuditAction.PAID_CAMPAIGN_DELETED, actor.id(), ENTITY, id, Map.of("name", campaign.getName()), client);
	}

	// --- monthly results --------------------------------------------------------------------------------------

	/**
	 * Records a month (no {@code version}) or corrects it ({@code version} of the existing row). Audited either way.
	 */
	@Transactional
	public CampaignDetail saveMonth(Long id, MarketingPeriod period, SaveMonth request, AuthenticatedUser actor,
			ClientInfo client) {
		PaidCampaign campaign = load(id);
		PaidResults results = request.results();
		requireRecordable(campaign, period, results);
		LocalDate today = calendar.today();
		PaidCampaignMonth row = monthRepository.findByCampaignIdAndMonthAndYear(id, period.month(), period.year()).orElse(null);
		String notes = StringUtils.hasText(request.notes()) ? request.notes().trim() : null;
		if (row == null) {
			insertMonth(campaign, period, results, notes, AdDataSource.MANUAL, userRepository.getReferenceById(actor.id()));
			auditService.record(AuditAction.PAID_RESULTS_RECORDED, actor.id(), ENTITY, id, monthDetails(period, results), client);
			return toDetail(campaign, actor);
		}
		if (request.version() == null) {
			throw ApiException.conflict("MONTH_EXISTS", period.label() + " is already recorded for this campaign. Correct that month instead.");
		}
		if (!MarketingMonths.canCorrect(period, today, actor.isSuperAdmin())) {
			throw ApiException.conflict("MONTH_LOCKED", period.label() + " is closed. Only the current and previous month can be corrected.");
		}
		if (!Objects.equals(row.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this month just now. Reload and try again.");
		}
		AuditChanges changes = new AuditChanges().track("results", row.results(), results).track("notes", row.getNotes(), notes);
		row.apply(results);
		row.setNotes(notes);
		monthRepository.flush();
		if (!changes.isEmpty()) {
			Map<String, Object> details = new HashMap<>(changes.toDetails());
			details.put("month", period.month());
			details.put("year", period.year());
			if (!MarketingMonths.isOpen(period, today)) {
				details.put("closedMonth", true);
			}
			auditService.record(AuditAction.PAID_RESULTS_CORRECTED, actor.id(), ENTITY, id, details, client);
		}
		return toDetail(campaign, actor);
	}

	// --- shared with the CSV importer -------------------------------------------------------------------------

	/** A month can hold results when the campaign has started (not a draft), runs in it, and it is not in the future. */
	void requireRecordable(PaidCampaign campaign, MarketingPeriod period, PaidResults results) {
		if (campaign.getStatus() == PaidCampaignStatus.DRAFT) {
			throw ApiException.badRequest("CAMPAIGN_NOT_STARTED", "A draft campaign has no results yet; set it to active first");
		}
		if (MarketingMonths.isFuture(period, calendar.today())) {
			throw ApiException.badRequest("FUTURE_PERIOD", "Results cannot be recorded for a future month");
		}
		if (!campaign.runsIn(period)) {
			throw ApiException.badRequest("MONTH_OUTSIDE_CAMPAIGN", period.label() + " is outside the campaign's dates");
		}
		List<PaidResults.Problem> problems = results.problems();
		if (!problems.isEmpty()) {
			throw ApiException.badRequest("INVALID_RESULTS", label(problems.getFirst().field()) + ": "
					+ problems.getFirst().message().toLowerCase(Locale.ROOT));
		}
	}

	PaidCampaignMonth insertMonth(PaidCampaign campaign, MarketingPeriod period, PaidResults results, String notes,
			AdDataSource source, User recordedBy) {
		PaidCampaignMonth row = new PaidCampaignMonth();
		row.setCampaign(campaign);
		row.setMonth(period.month());
		row.setYear(period.year());
		row.apply(results);
		row.setNotes(notes);
		row.setSource(source);
		row.setRecordedBy(recordedBy);
		return monthRepository.save(row);
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private CampaignDetail toDetail(PaidCampaign campaign, AuthenticatedUser viewer) {
		LocalDate today = calendar.today();
		boolean canEdit = viewer.hasPermission(CAMPAIGN_EDIT);
		List<PaidCampaignMonth> rows = monthRepository.findByCampaignIdOrderByYearAscMonthAsc(campaign.getId());
		PaidResults lifetime = rows.stream().map(PaidCampaignMonth::results).reduce(PaidResults.ZERO, PaidResults::plus);
		List<MonthRow> months = rows.stream().map(m -> {
			MarketingPeriod period = new MarketingPeriod(m.getMonth(), m.getYear());
			return new MonthRow(m.getId(), Period.of(period), Figures.of(m.results()), m.getNotes(), m.getSource(),
					UserSummary.of(m.getRecordedBy()), m.getUpdatedAt(), m.getVersion(),
					canEdit && MarketingMonths.canCorrect(period, today, viewer.isSuperAdmin()));
		}).toList();
		return new CampaignDetail(toItem(campaign, lifetime, null), months, new CampaignPermissions(canEdit));
	}

	private static CampaignItem toItem(PaidCampaign c, PaidResults lifetime, PaidResults month) {
		return new CampaignItem(c.getId(), c.getName(), c.getPlatform(), c.getObjective(), c.getStartDate(), c.getEndDate(),
				c.getBudget(), c.getCurrency(), UserSummary.of(c.getOwner()), c.getStatus(), c.getNotes(), c.getProvider(),
				c.getExternalId(), Figures.of(lifetime), BudgetProgress.of(c.getBudget(), lifetime.spend()),
				month == null ? null : Figures.of(month), c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
	}

	private MonthTotals monthTotals(MarketingPeriod period, Long ownerId, AdPlatform platform) {
		Totals t = campaignQuery.monthly(period, period, ownerId, platform).getOrDefault(period, Totals.EMPTY);
		return new MonthTotals(Period.of(period), t.campaigns(), Figures.of(t.results()));
	}

	private static void applyPlan(PaidCampaign campaign, SaveCampaign request, User owner) {
		campaign.setName(request.name().trim());
		campaign.setPlatform(platformOf(request));
		campaign.setObjective(request.objective());
		campaign.setStartDate(request.startDate());
		campaign.setEndDate(request.endDate());
		campaign.setBudget(money(request.budget()));
		campaign.setCurrency(request.currency() == null ? "INR" : request.currency());
		campaign.setOwner(owner);
		campaign.setStatus(request.status());
		campaign.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);
	}

	/** New dates must still cover every recorded month. */
	private void requireDatesCoverResults(Long id, LocalDate start, LocalDate end) {
		Object[] range = monthRepository.findMonthRange(id).getFirst();
		if (range[0] == null) {
			return;
		}
		int first = ((Number) range[0]).intValue();
		int last = ((Number) range[1]).intValue();
		MarketingPeriod firstMonth = new MarketingPeriod((first - 1) % 12 + 1, (first - 1) / 12);
		MarketingPeriod lastMonth = new MarketingPeriod((last - 1) % 12 + 1, (last - 1) / 12);
		if (start.isAfter(firstMonth.lastDay()) || (end != null && end.isBefore(lastMonth.firstDay()))) {
			throw ApiException.conflict("RESULTS_OUTSIDE_DATES", "Results are recorded from " + firstMonth.label() + " to "
					+ lastMonth.label() + "; the campaign's dates must still include those months");
		}
	}

	private static Specification<PaidCampaign> spec(String search, Set<AdPlatform> platforms,
			Set<PaidCampaignStatus> statuses, Long ownerId, MarketingPeriod period) {
		Specification<PaidCampaign> spec = (root, query, cb) -> cb.conjunction();
		if (StringUtils.hasText(search)) {
			String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
				.replace("_", "\\_") + "%";
			spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
		}
		if (platforms != null && !platforms.isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("platform").in(platforms));
		}
		if (statuses != null && !statuses.isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("status").in(statuses));
		}
		if (ownerId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId));
		}
		if (period != null) {
			// Running in the month: started by its end, and not ended before it began.
			spec = spec.and((root, query, cb) -> cb.and(cb.lessThanOrEqualTo(root.get("startDate"), period.lastDay()),
					cb.or(cb.isNull(root.get("endDate")), cb.greaterThanOrEqualTo(root.get("endDate"), period.firstDay()))));
		}
		return spec;
	}

	private PaidCampaign load(Long id) {
		return campaignRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("PAID_CAMPAIGN_NOT_FOUND", "Campaign not found"));
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

	private static Map<String, Object> monthDetails(MarketingPeriod period, PaidResults results) {
		Map<String, Object> details = new HashMap<>();
		details.put("month", period.month());
		details.put("year", period.year());
		details.put("results", results);
		return details;
	}

	private static void validateDates(LocalDate start, LocalDate end) {
		if (end != null && end.isBefore(start)) {
			throw ApiException.badRequest("INVALID_DATES", "The end date cannot be before the start date");
		}
	}

	private static AdPlatform platformOf(SaveCampaign request) {
		return request.platform() == null ? AdPlatform.LINKEDIN : request.platform();
	}

	private static BigDecimal money(BigDecimal value) {
		return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	/** "conversions" → "Conversions". */
	static String label(String field) {
		return Character.toUpperCase(field.charAt(0)) + field.substring(1);
	}

	private static String plain(BigDecimal value) {
		return value == null ? "" : value.toPlainString();
	}

	private static Long userId(User user) {
		return user == null ? null : user.getId();
	}

}
