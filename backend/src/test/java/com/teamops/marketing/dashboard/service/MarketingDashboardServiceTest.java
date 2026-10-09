package com.teamops.marketing.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.activity.repository.ActivityQuery;
import com.teamops.marketing.activity.service.ActivityService;
import com.teamops.marketing.backlink.repository.BacklinkQuery;
import com.teamops.marketing.backlink.repository.BacklinkQuery.StageCounts;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.TargetProgress.TargetStatus;
import com.teamops.marketing.content.repository.ContentQuery;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.Dashboard;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.email.repository.EmailCampaignQuery;
import com.teamops.marketing.email.service.EmailCounts;
import com.teamops.marketing.lead.repository.LeadQuery;
import com.teamops.marketing.paid.entity.AdPlatform;
import com.teamops.marketing.paid.repository.PaidCampaignQuery;
import com.teamops.marketing.paid.service.PaidResults;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.marketing.seo.repository.SeoRankingQuery;
import com.teamops.marketing.seo.service.KeywordStanding;
import com.teamops.marketing.target.dto.TargetDtos.MonthlyTargets;
import com.teamops.marketing.target.dto.TargetDtos.StatusSummary;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.marketing.target.dto.TargetDtos.TypeRef;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetUnit;
import com.teamops.marketing.target.service.TargetRules.ActualOrigin;
import com.teamops.marketing.target.service.TargetService;
import com.teamops.user.dto.UserSummary;

/** The dashboard's assembly with every query mocked: brief figures, permission gating and one query per module. */
@ExtendWith(MockitoExtension.class)
class MarketingDashboardServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

	private static final MarketingPeriod OCTOBER = new MarketingPeriod(10, 2026);

	private static final MarketingPeriod SEPTEMBER = new MarketingPeriod(9, 2026);

	private static final AuthenticatedUser EXECUTIVE = new AuthenticatedUser(1L, "rakesh@teamops.local", "Rakesh", 1L,
			Set.of("SUPER_ADMIN"), Set.of("MARKETING_VIEW", "SEO_VIEW", "LEAD_VIEW", "CAMPAIGN_VIEW", "BACKLINK_VIEW",
					"CONTENT_VIEW", "TARGET_VIEW"));

	/** Marketing access only: the activities and an empty trend, nothing else. */
	private static final AuthenticatedUser MARKETING_ONLY = new AuthenticatedUser(9L, "viewer@teamops.local", "Viewer", 7L,
			Set.of("EMPLOYEE"), Set.of("MARKETING_VIEW"));

	private static final UserSummary PRIYA = new UserSummary(5L, "Priya Menon", "priya.menon@teamops.local", null, null);

	@Mock
	private SeoPageRepository pageRepository;

	@Mock
	private SeoRankingQuery rankingQuery;

	@Mock
	private LeadQuery leadQuery;

	@Mock
	private EmailCampaignQuery emailQuery;

	@Mock
	private PaidCampaignQuery paidQuery;

	@Mock
	private BacklinkQuery backlinkQuery;

	@Mock
	private ContentQuery contentQuery;

	@Mock
	private ActivityQuery activityQuery;

	@Mock
	private ActivityService activityService;

	@Mock
	private TargetService targetService;

	@Mock
	private BusinessCalendar calendar;

	@InjectMocks
	private MarketingDashboardService service;

	@BeforeEach
	void setUp() {
		lenient().when(calendar.today()).thenReturn(TODAY);
		lenient().when(activityQuery.month(any(), any(), any())).thenReturn(new ActivityQuery.MonthCounts(10, 6, 2, 2, 1));
		lenient().when(activityService.occurrences(any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(PageResponse.of(new PageImpl<>(List.of())));
	}

	@Test
	void briefSection49ScorecardComesFromOneQueryPerModule() {
		when(targetService.monthly(OCTOBER, null, null, EXECUTIVE)).thenReturn(new MonthlyTargets(Period.of(OCTOBER),
				List.of(target(1L, ActualSource.LEADS, "250", "200", "80", "50", TargetStatus.IN_PROGRESS, PRIYA),
						target(7L, ActualSource.BACKLINKS_LIVE, "50", "22", "44", "28", TargetStatus.BEHIND, PRIYA),
						target(8L, ActualSource.BLOGS_PUBLISHED, "12", "9", "75", "3", TargetStatus.IN_PROGRESS, null)),
				new StatusSummary(3, 0, 2, 1), List.of()));
		when(pageRepository.countByStatusNot(any())).thenReturn(6L);
		when(rankingQuery.forReport(null, null, OCTOBER)).thenReturn(List.of(row(1, 7, 9), row(2, 15, 12), row(3, null, 40)));
		when(rankingQuery.forReport(null, null, SEPTEMBER)).thenReturn(List.of(row(1, 9, 9), row(2, 12, 12), row(3, 40, 40)));
		when(rankingQuery.top10ByMonth(any(), eq(OCTOBER), isNull())).thenReturn(Map.of(OCTOBER, 1L));
		when(leadQuery.monthly(any(), eq(OCTOBER), isNull(), isNull())).thenReturn(Map.of(
				OCTOBER, Map.of(LeadSource.ORGANIC, 80L, LeadSource.EMAIL, 45L, LeadSource.LINKEDIN, 35L, LeadSource.BLOG, 20L,
						LeadSource.PAID_CAMPAIGN, 20L),
				SEPTEMBER, Map.of(LeadSource.ORGANIC, 180L)));
		// Brief section 49: 25,000 sent, 8,500 opened, 1,250 clicked, 185 leads.
		when(emailQuery.monthly(any(), eq(OCTOBER), isNull())).thenReturn(Map.of(OCTOBER,
				new EmailCampaignQuery.Totals(1, new EmailCounts(25_000, 24_000, 1_000, 10_000, 8_500, 2_000, 1_250, 40, 185))));
		// LinkedIn: ₹42,000 spend, 84 leads.
		when(paidQuery.monthly(any(), eq(OCTOBER), isNull(), eq(AdPlatform.LINKEDIN))).thenReturn(Map.of(OCTOBER,
				new PaidCampaignQuery.Totals(1, new PaidResults(new BigDecimal("42000"), 150_000, 2_800, 84, 21))));
		when(paidQuery.running(OCTOBER, null, AdPlatform.LINKEDIN))
			.thenReturn(new PaidCampaignQuery.Budget(1, new BigDecimal("50000"), new BigDecimal("42000")));
		when(backlinkQuery.monthly(any(), eq(OCTOBER), isNull())).thenReturn(Map.of(OCTOBER, new StageCounts(35, 28, 22, 2, 1)));
		when(contentQuery.monthly(any(), eq(OCTOBER), isNull())).thenReturn(Map.of(OCTOBER, new ContentQuery.MonthFigures(12, 9, 10, 1, 45)));

		Dashboard d = service.dashboard(OCTOBER, null, null, EXECUTIVE);

		assertThat(d.comparisonPeriod().label()).isEqualTo("September 2026");
		assertThat(d.seo().totalPages()).isEqualTo(6);
		assertThat(d.seo().current().top10()).isEqualTo(1);
		assertThat(d.seo().current().notRanked()).isEqualTo(1);
		assertThat(d.seo().current().improved()).isEqualTo(1);
		assertThat(d.seo().current().declined()).isEqualTo(2);

		// Lead generation: target 250, achieved 200, 80%, 50 remaining.
		assertThat(d.leads().total()).isEqualTo(200);
		assertThat(d.leads().comparisonTotal()).isEqualTo(180);
		assertThat(d.leads().target().achievementPct()).isEqualByComparingTo("80");
		assertThat(d.leads().target().remaining()).isEqualByComparingTo("50");
		assertThat(d.leads().bySource()).hasSize(LeadSource.values().length);

		assertThat(d.email().current().rates().openRate()).isEqualByComparingTo("35.42");
		assertThat(d.linkedin().current().rates().costPerLead()).isEqualByComparingTo("500");
		assertThat(d.linkedin().budget().remaining()).isEqualByComparingTo("8000");

		// Backlinks: 50 target, 35 submitted, 22 live, 15 still to submit. Content: 12 target, 9 published, 3 remaining.
		assertThat(d.backlinks().current().submitted()).isEqualTo(35);
		assertThat(d.backlinks().current().live()).isEqualTo(22);
		assertThat(d.backlinks().target().remaining()).isEqualByComparingTo("15");
		assertThat(d.content().current().publishedBlogs()).isEqualTo(9);
		assertThat(d.content().current().leads()).isEqualTo(45);
		assertThat(d.content().blogTarget().remaining()).isEqualByComparingTo("3");

		assertThat(d.targets().summary()).isEqualTo(new StatusSummary(3, 0, 2, 1));
		// Ten due, two skipped, six done: 6 ÷ 8 = 75%.
		assertThat(d.activities().completionPct()).isEqualByComparingTo("75");
		assertThat(d.activities().overdue()).isEqualTo(1);

		// Six months by default, oldest first, ending with the month.
		assertThat(d.trend()).hasSize(6);
		assertThat(d.trend().getLast().period().label()).isEqualTo("October 2026");
		assertThat(d.trend().getLast().leads()).isEqualTo(200);
		assertThat(d.trend().getLast().emailLeads()).isEqualTo(45);
		assertThat(d.trend().getLast().top10Keywords()).isEqualTo(1);
		assertThat(d.trend().getLast().backlinksLive()).isEqualTo(22);
		assertThat(d.trend().getFirst().top10Keywords()).isNull();
		assertThat(d.trend().getFirst().leads()).isZero();

		// One aggregation per module over the trend range, which covers the month and the month before.
		MarketingPeriod may = new MarketingPeriod(5, 2026);
		verify(leadQuery, times(1)).monthly(may, OCTOBER, null, null);
		verify(emailQuery, times(1)).monthly(may, OCTOBER, null);
		verify(paidQuery, times(1)).monthly(may, OCTOBER, null, AdPlatform.LINKEDIN);
		verify(backlinkQuery, times(1)).monthly(may, OCTOBER, null);
		verify(contentQuery, times(1)).monthly(may, OCTOBER, null);
		verify(targetService, times(1)).monthly(OCTOBER, null, null, EXECUTIVE);
	}

	@Test
	void sectionsNeedTheirModulesViewPermission() {
		Dashboard d = service.dashboard(OCTOBER, null, 3, MARKETING_ONLY);

		assertThat(d.seo()).isNull();
		assertThat(d.leads()).isNull();
		assertThat(d.email()).isNull();
		assertThat(d.linkedin()).isNull();
		assertThat(d.backlinks()).isNull();
		assertThat(d.content()).isNull();
		assertThat(d.targets()).isNull();
		assertThat(d.targetsVisible()).isFalse();
		assertThat(d.activities()).isNotNull();
		assertThat(d.trend()).hasSize(3).allSatisfy(m -> assertThat(m.leads()).isNull());
		verifyNoInteractions(rankingQuery, leadQuery, emailQuery, paidQuery, backlinkQuery, contentQuery, targetService,
				pageRepository);
	}

	@Test
	void theOwnerFilterNarrowsTheRecordsButKeepsTheTeamsTargets() {
		AuthenticatedUser leadsAndTargets = new AuthenticatedUser(9L, "x@teamops.local", "X", 7L, Set.of("EMPLOYEE"),
				Set.of("MARKETING_VIEW", "LEAD_VIEW", "TARGET_VIEW"));
		when(targetService.monthly(OCTOBER, null, null, leadsAndTargets)).thenReturn(new MonthlyTargets(Period.of(OCTOBER),
				List.of(target(1L, ActualSource.LEADS, "250", "200", "80", "50", TargetStatus.IN_PROGRESS, PRIYA),
						target(8L, ActualSource.BLOGS_PUBLISHED, "12", "9", "75", "3", TargetStatus.IN_PROGRESS, null)),
				new StatusSummary(2, 0, 2, 0), List.of()));
		when(leadQuery.monthly(any(), eq(OCTOBER), eq(5L), isNull())).thenReturn(Map.of(OCTOBER, Map.of(LeadSource.EMAIL, 4L)));

		Dashboard d = service.dashboard(OCTOBER, 5L, 24, leadsAndTargets);

		assertThat(d.leads().total()).isEqualTo(4);
		assertThat(d.leads().target().targetValue()).isEqualByComparingTo("250");
		// The target list shows the owner's targets only.
		assertThat(d.targets().targets()).extracting(t -> t.type().actualSource()).containsExactly(ActualSource.LEADS);
		assertThat(d.targets().summary().total()).isEqualTo(1);
		assertThat(d.trend()).hasSize(24);
		verify(activityQuery).month(OCTOBER, 5L, TODAY);
		verify(targetService, never()).monthly(any(), eq(5L), any(), any());
	}

	@Test
	void completionLeavesSkippedOccurrencesOut() {
		assertThat(MarketingDashboardService.completionPct(6, 10, 2)).isEqualByComparingTo("75");
		assertThat(MarketingDashboardService.completionPct(0, 2, 2)).isNull();
	}

	private static SeoRankingQuery.Row row(long id, Integer position, Integer previous) {
		return new SeoRankingQuery.Row(id, 1L, KeywordStatus.ACTIVE, KeywordStanding.of(true, position, true, previous), null);
	}

	private static TargetItem target(Long id, ActualSource source, String value, String actual, String pct, String remaining,
			TargetStatus status, UserSummary owner) {
		return new TargetItem(id, new TypeRef(id, source.name(), source.name(), TargetUnit.COUNT, source, true), 10, 2026,
				"October 2026", new BigDecimal(value), new BigDecimal(actual), ActualOrigin.AUTOMATIC, new BigDecimal(pct),
				new BigDecimal(remaining), status, new BigDecimal("60"), owner, null, null, false, false, 0, null, null);
	}

}
