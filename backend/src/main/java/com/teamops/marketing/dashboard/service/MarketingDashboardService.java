package com.teamops.marketing.dashboard.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.marketing.activity.entity.OccurrenceStatus;
import com.teamops.marketing.activity.repository.ActivityQuery;
import com.teamops.marketing.activity.service.ActivityService;
import com.teamops.marketing.backlink.dto.BacklinkDtos.MonthActivity;
import com.teamops.marketing.backlink.dto.BacklinkDtos.MonthTarget;
import com.teamops.marketing.backlink.repository.BacklinkQuery;
import com.teamops.marketing.backlink.repository.BacklinkQuery.StageCounts;
import com.teamops.marketing.backlink.service.BacklinkRules;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingMath;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.TargetProgress.TargetStatus;
import com.teamops.marketing.content.dto.ContentDtos.BlogTarget;
import com.teamops.marketing.content.dto.ContentDtos.MonthFigures;
import com.teamops.marketing.content.repository.ContentQuery;
import com.teamops.marketing.content.service.ContentRules;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.ActivitySection;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.BacklinkSection;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.ContentSection;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.Dashboard;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.EmailMonth;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.EmailSection;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.LeadSection;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.LinkedInSection;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.PaidMonth;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.SeoSection;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.SourceCount;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.TargetSection;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.TrendMonth;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.email.repository.EmailCampaignQuery;
import com.teamops.marketing.email.service.EmailRates;
import com.teamops.marketing.lead.repository.LeadQuery;
import com.teamops.marketing.paid.entity.AdPlatform;
import com.teamops.marketing.paid.repository.PaidCampaignQuery;
import com.teamops.marketing.paid.service.BudgetProgress;
import com.teamops.marketing.paid.service.PaidRates;
import com.teamops.marketing.seo.entity.PageStatus;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.marketing.seo.repository.SeoRankingQuery;
import com.teamops.marketing.seo.service.SeoStats;
import com.teamops.marketing.target.dto.TargetDtos.StatusSummary;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.service.TargetService;

import lombok.RequiredArgsConstructor;

/**
 * The Digital Marketing executive dashboard (brief sections 22, 49, 61 and 70): every module for one month against
 * the month before, the month's targets, recurring activities and a monthly trend, from a handful of grouped
 * queries. Each module runs one aggregation over the whole trend range (which also covers the month and the month
 * before), and only when the viewer holds that module's view permission; targets need TARGET_VIEW. The owner filter
 * narrows each module's records to their owner (activities: the person doing them); targets stay the team's, and the
 * target list shows the owner's targets.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingDashboardService {

	public static final int DEFAULT_TREND_MONTHS = 6;

	public static final int MAX_TREND_MONTHS = 24;

	static final int ATTENTION = 5;

	private static final Set<OccurrenceStatus> OPEN = Set.of(OccurrenceStatus.PENDING, OccurrenceStatus.IN_PROGRESS);

	private final SeoPageRepository pageRepository;

	private final SeoRankingQuery rankingQuery;

	private final LeadQuery leadQuery;

	private final EmailCampaignQuery emailQuery;

	private final PaidCampaignQuery paidQuery;

	private final BacklinkQuery backlinkQuery;

	private final ContentQuery contentQuery;

	private final ActivityQuery activityQuery;

	private final ActivityService activityService;

	private final TargetService targetService;

	private final BusinessCalendar calendar;

	public MarketingPeriod period(Integer month, Integer year) {
		return MarketingPeriod.resolve(month, year, calendar.today());
	}

	/** {@code months}: the trend length (default 6, from 2 to 24), ending with {@code period}. */
	public Dashboard dashboard(MarketingPeriod period, Long ownerId, Integer months, AuthenticatedUser viewer) {
		LocalDate today = calendar.today();
		MarketingPeriod comparison = period.previous();
		int count = months == null ? DEFAULT_TREND_MONTHS : Math.clamp(months, 2, MAX_TREND_MONTHS);
		MarketingPeriod start = period.plusMonths(-(count - 1));

		boolean targetsVisible = viewer.hasPermission("TARGET_VIEW");
		List<TargetItem> targets = targetsVisible ? targetService.monthly(period, null, null, viewer).targets() : List.of();

		// SEO: the month's standings and the month before (two queries), page count, and top 10 per month.
		SeoSection seo = null;
		Map<MarketingPeriod, Long> top10 = Map.of();
		if (viewer.hasPermission("SEO_VIEW")) {
			long pages = ownerId == null ? pageRepository.countByStatusNot(PageStatus.ARCHIVED)
					: pageRepository.countByStatusNotAndOwner_Id(PageStatus.ARCHIVED, ownerId);
			seo = new SeoSection(pages, seoStats(period, ownerId), seoStats(comparison, ownerId));
			top10 = rankingQuery.top10ByMonth(start, period, ownerId);
		}

		LeadSection leads = null;
		Map<MarketingPeriod, Map<LeadSource, Long>> leadCounts = Map.of();
		if (viewer.hasPermission("LEAD_VIEW")) {
			leadCounts = leadQuery.monthly(start, period, ownerId, null);
			Map<LeadSource, Long> now = leadCounts.getOrDefault(period, Map.of());
			Map<LeadSource, Long> before = leadCounts.getOrDefault(comparison, Map.of());
			List<SourceCount> bySource = new ArrayList<>();
			for (LeadSource source : LeadSource.values()) {
				bySource.add(new SourceCount(source, now.getOrDefault(source, 0L), before.getOrDefault(source, 0L)));
			}
			leads = new LeadSection(sum(now), sum(before), bySource, targetOf(targets, ActualSource.LEADS));
		}

		EmailSection email = null;
		LinkedInSection linkedin = null;
		Map<MarketingPeriod, EmailCampaignQuery.Totals> emailTotals = Map.of();
		Map<MarketingPeriod, PaidCampaignQuery.Totals> paidTotals = Map.of();
		if (viewer.hasPermission("CAMPAIGN_VIEW")) {
			emailTotals = emailQuery.monthly(start, period, ownerId);
			email = new EmailSection(emailMonth(emailTotals.getOrDefault(period, EmailCampaignQuery.Totals.EMPTY)),
					emailMonth(emailTotals.getOrDefault(comparison, EmailCampaignQuery.Totals.EMPTY)));
			paidTotals = paidQuery.monthly(start, period, ownerId, AdPlatform.LINKEDIN);
			PaidCampaignQuery.Budget budget = paidQuery.running(period, ownerId, AdPlatform.LINKEDIN);
			linkedin = new LinkedInSection(paidMonth(paidTotals.getOrDefault(period, PaidCampaignQuery.Totals.EMPTY)),
					paidMonth(paidTotals.getOrDefault(comparison, PaidCampaignQuery.Totals.EMPTY)), budget.campaigns(),
					BudgetProgress.of(budget.budget(), budget.spent()));
		}

		BacklinkSection backlinks = null;
		Map<MarketingPeriod, StageCounts> stageCounts = Map.of();
		if (viewer.hasPermission("BACKLINK_VIEW")) {
			stageCounts = backlinkQuery.monthly(start, period, ownerId);
			StageCounts now = stageCounts.getOrDefault(period, StageCounts.ZERO);
			TargetItem goal = targetOf(targets, ActualSource.BACKLINKS_LIVE);
			backlinks = new BacklinkSection(activity(period, now),
					activity(comparison, stageCounts.getOrDefault(comparison, StageCounts.ZERO)),
					goal == null ? null
							: new MonthTarget(goal.targetValue(), BacklinkRules.remaining(goal.targetValue(), now.submitted()), goal));
		}

		ContentSection content = null;
		Map<MarketingPeriod, ContentQuery.MonthFigures> contentFigures = Map.of();
		if (viewer.hasPermission("CONTENT_VIEW")) {
			contentFigures = contentQuery.monthly(start, period, ownerId);
			ContentQuery.MonthFigures now = contentFigures.getOrDefault(period, ContentQuery.MonthFigures.ZERO);
			TargetItem goal = targetOf(targets, ActualSource.BLOGS_PUBLISHED);
			content = new ContentSection(figures(period, now),
					figures(comparison, contentFigures.getOrDefault(comparison, ContentQuery.MonthFigures.ZERO)),
					goal == null ? null
							: new BlogTarget(goal.targetValue(), ContentRules.remaining(goal.targetValue(), now.publishedBlogs()), goal));
		}

		TargetSection targetSection = null;
		if (targetsVisible) {
			List<TargetItem> shown = targets.stream()
				.filter(t -> ownerId == null || (t.owner() != null && t.owner().id().equals(ownerId)))
				.toList();
			targetSection = new TargetSection(shown, summarise(shown));
		}

		ActivityQuery.MonthCounts counts = activityQuery.month(period, ownerId, today);
		ActivitySection activities = new ActivitySection(counts.due(), counts.completed(), counts.skipped(), counts.open(),
				counts.overdue(), completionPct(counts.completed(), counts.due(), counts.skipped()),
				activityService.occurrences(OPEN, null, period.lastDay(), ownerId, null,
						PageRequest.of(0, ATTENTION, Sort.by("dueDate", "id")), viewer).content());

		List<TrendMonth> trend = new ArrayList<>();
		for (MarketingPeriod p = start; !p.firstDay().isAfter(period.firstDay()); p = p.next()) {
			trend.add(new TrendMonth(Period.of(p), leads == null ? null : sum(leadCounts.getOrDefault(p, Map.of())),
					seo == null ? null : top10.get(p),
					leads == null ? null : leadCounts.getOrDefault(p, Map.of()).getOrDefault(LeadSource.EMAIL, 0L),
					linkedin == null ? null : paidTotals.getOrDefault(p, PaidCampaignQuery.Totals.EMPTY).results().spend(),
					linkedin == null ? null : paidTotals.getOrDefault(p, PaidCampaignQuery.Totals.EMPTY).results().leads(),
					backlinks == null ? null : stageCounts.getOrDefault(p, StageCounts.ZERO).live(),
					content == null ? null : contentFigures.getOrDefault(p, ContentQuery.MonthFigures.ZERO).publishedBlogs()));
		}

		return new Dashboard(Period.of(period), Period.of(comparison), today, targetsVisible, seo, leads, email, linkedin,
				backlinks, content, targetSection, activities, trend);
	}

	private SeoStats seoStats(MarketingPeriod period, Long ownerId) {
		return SeoStats.of(rankingQuery.forReport(null, ownerId, period).stream().map(SeoRankingQuery.Row::standing).toList());
	}

	/** The month's target whose actual comes from {@code source} (the first by position), or null. */
	private static TargetItem targetOf(List<TargetItem> targets, ActualSource source) {
		return targets.stream().filter(t -> t.type().actualSource() == source).findFirst().orElse(null);
	}

	private static EmailMonth emailMonth(EmailCampaignQuery.Totals totals) {
		return new EmailMonth(totals.campaigns(), totals.counts(), EmailRates.of(totals.counts()));
	}

	private static PaidMonth paidMonth(PaidCampaignQuery.Totals totals) {
		return new PaidMonth(totals.campaigns(), totals.results(), PaidRates.of(totals.results()));
	}

	private static MonthActivity activity(MarketingPeriod period, StageCounts c) {
		return new MonthActivity(Period.of(period), c.submitted(), c.approved(), c.live(), c.rejected(), c.lost());
	}

	private static MonthFigures figures(MarketingPeriod period, ContentQuery.MonthFigures f) {
		return new MonthFigures(Period.of(period), f.plannedBlogs(), f.publishedBlogs(), f.publishedAll(), f.refreshed(),
				f.leads());
	}

	private static StatusSummary summarise(List<TargetItem> items) {
		int achieved = 0;
		int inProgress = 0;
		int behind = 0;
		for (TargetItem item : items) {
			if (item.status() == TargetStatus.ACHIEVED) {
				achieved++;
			}
			else if (item.status() == TargetStatus.IN_PROGRESS) {
				inProgress++;
			}
			else if (item.status() == TargetStatus.BEHIND) {
				behind++;
			}
		}
		return new StatusSummary(items.size(), achieved, inProgress, behind);
	}

	private static long sum(Map<LeadSource, Long> counts) {
		return counts.values().stream().mapToLong(Long::longValue).sum();
	}

	/** Exposed for tests: the share of the month's occurrences done, skipped ones left out. */
	static BigDecimal completionPct(long completed, long due, long skipped) {
		return MarketingMath.percent(completed, due - skipped);
	}

}
