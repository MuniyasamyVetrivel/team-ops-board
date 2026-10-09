package com.teamops.marketing.report.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.marketing.activity.repository.ActivityQuery;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingMath;
import com.teamops.marketing.common.MarketingMonths;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.RankingChange.Movement;
import com.teamops.marketing.dashboard.dto.MarketingDashboardDtos.Dashboard;
import com.teamops.marketing.dashboard.service.MarketingDashboardService;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.report.dto.MarketingReportDtos.Better;
import com.teamops.marketing.report.dto.MarketingReportDtos.FrozenMonth;
import com.teamops.marketing.report.dto.MarketingReportDtos.Group;
import com.teamops.marketing.report.dto.MarketingReportDtos.KeywordMove;
import com.teamops.marketing.report.dto.MarketingReportDtos.KeywordMovements;
import com.teamops.marketing.report.dto.MarketingReportDtos.Line;
import com.teamops.marketing.report.dto.MarketingReportDtos.MonthlyReport;
import com.teamops.marketing.report.dto.MarketingReportDtos.TargetRow;
import com.teamops.marketing.report.dto.MarketingReportDtos.Unit;
import com.teamops.marketing.report.entity.MarketingMonthlyReport;
import com.teamops.marketing.report.repository.MarketingMonthlyReportRepository;
import com.teamops.marketing.seo.dto.SeoDtos.KeywordItem;
import com.teamops.marketing.seo.repository.SeoRankingTableQuery;
import com.teamops.marketing.seo.service.KeywordRankingService;
import com.teamops.marketing.seo.service.SeoStats;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.service.TargetService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * The Digital Marketing monthly report (brief section 50): SEO summary and keyword movements, leads, email, LinkedIn,
 * backlinks, content, targets and recurring activities for a month against the month before, built on the marketing
 * dashboard's aggregates (the same rules, the same permissions). An ended month can be frozen (MARKETING_EDIT): the
 * team-wide report is kept as it stood, and is what that month's report shows from then on unless {@code live} is
 * asked for; readers still only see the parts their permissions allow.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingReportService {

	public static final int KEYWORD_MOVES = 10;

	/** Every view permission the full report needs: freezing requires them all, so a snapshot is never partial. */
	static final Set<String> FULL_ACCESS = Set.of("MARKETING_VIEW", "SEO_VIEW", "LEAD_VIEW", "CAMPAIGN_VIEW",
			"BACKLINK_VIEW", "CONTENT_VIEW", "TARGET_VIEW");

	/** The view permission of each group. */
	static final Map<String, String> GROUP_PERMISSIONS = Map.of("SEO", "SEO_VIEW", "LEADS", "LEAD_VIEW", "EMAIL",
			"CAMPAIGN_VIEW", "LINKEDIN", "CAMPAIGN_VIEW", "BACKLINKS", "BACKLINK_VIEW", "CONTENT", "CONTENT_VIEW",
			"ACTIVITIES", "MARKETING_VIEW");

	static final String ENTITY = "MARKETING_MONTHLY_REPORT";

	private final MarketingDashboardService dashboardService;

	private final KeywordRankingService rankingService;

	private final TargetService targetService;

	private final ActivityQuery activityQuery;

	private final MarketingMonthlyReportRepository reportRepository;

	private final UserRepository userRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	private final ObjectMapper objectMapper;

	public MarketingPeriod period(Integer month, Integer year) {
		return MarketingPeriod.resolve(month, year, calendar.today());
	}

	/** The frozen report for a team-wide month when there is one (unless {@code live}), else the report as of now. */
	public MonthlyReport report(MarketingPeriod period, Long ownerId, boolean live, AuthenticatedUser viewer) {
		if (!live && ownerId == null) {
			MarketingMonthlyReport frozen = reportRepository.findByYearAndMonth(period.year(), period.month()).orElse(null);
			if (frozen != null) {
				return visibleTo(read(frozen), viewer);
			}
		}
		return live(period, ownerId, viewer);
	}

	public List<FrozenMonth> frozenMonths() {
		return reportRepository.findAllByOrderByYearDescMonthDesc()
			.stream()
			.map(r -> new FrozenMonth(Period.of(new MarketingPeriod(r.getMonth(), r.getYear())), r.getGeneratedAt(),
					UserSummary.of(r.getGeneratedBy())))
			.toList();
	}

	/**
	 * Freezes an ended month's team-wide report: insert-only, once per month (409 {@code REPORT_FROZEN}), not for
	 * the current or a future month (400 {@code MONTH_NOT_ENDED}), and only by someone who can see every module
	 * (403 {@code INCOMPLETE_ACCESS}). Audited.
	 */
	@Transactional
	public MonthlyReport freeze(MarketingPeriod period, AuthenticatedUser actor, ClientInfo client) {
		if (!FULL_ACCESS.stream().allMatch(actor::hasPermission)) {
			throw ApiException.forbidden("INCOMPLETE_ACCESS", "Freezing a report needs view access to every marketing module");
		}
		LocalDate today = calendar.today();
		if (MarketingMonths.isFuture(period, today) || period.equals(MarketingPeriod.of(today))) {
			throw ApiException.badRequest("MONTH_NOT_ENDED", period.label() + " has not ended yet");
		}
		if (reportRepository.existsByYearAndMonth(period.year(), period.month())) {
			throw ApiException.conflict("REPORT_FROZEN", "The " + period.label() + " report is already frozen");
		}
		MonthlyReport live = live(period, null, actor);
		MarketingMonthlyReport snapshot = new MarketingMonthlyReport();
		snapshot.setMonth(period.month());
		snapshot.setYear(period.year());
		snapshot.setGeneratedBy(userRepository.getReferenceById(actor.id()));
		snapshot.setGeneratedAt(calendar.now());
		snapshot.setPayload(objectMapper.writeValueAsString(live));
		reportRepository.saveAndFlush(snapshot);
		auditService.record(AuditAction.MARKETING_REPORT_FROZEN, actor.id(), ENTITY, snapshot.getId(),
				Map.of("month", period.month(), "year", period.year()), client);
		return report(period, null, false, actor);
	}

	// --- building ---------------------------------------------------------------------------------------------

	MonthlyReport live(MarketingPeriod period, Long ownerId, AuthenticatedUser viewer) {
		MarketingPeriod previous = period.previous();
		Dashboard d = dashboardService.dashboard(period, ownerId, 2, viewer);
		Map<ActualSource, TargetItem> targetsNow = new HashMap<>();
		Map<Long, TargetItem> previousByType = new HashMap<>();
		Map<ActualSource, TargetItem> targetsBefore = new HashMap<>();
		if (d.targetsVisible()) {
			// The dashboard already holds the team's targets unless it was narrowed to one owner.
			List<TargetItem> team = ownerId == null && d.targets() != null ? d.targets().targets()
					: targetService.monthly(period, null, null, viewer).targets();
			team.forEach(t -> targetsNow.putIfAbsent(t.type().actualSource(), t));
			for (TargetItem t : targetService.monthly(previous, null, null, viewer).targets()) {
				previousByType.put(t.type().id(), t);
				targetsBefore.putIfAbsent(t.type().actualSource(), t);
			}
		}

		List<Group> groups = new ArrayList<>();
		if (d.seo() != null) {
			SeoStats c = d.seo().current();
			SeoStats p = d.seo().comparison();
			groups.add(new Group("SEO", "SEO summary", List.of(
					line("Keywords tracked", Unit.COUNT, Better.NEITHER, c.totalKeywords(), p.totalKeywords()),
					line("Top 3 keywords", Unit.COUNT, Better.HIGHER, c.top3(), p.top3()),
					line("Top 10 keywords", Unit.COUNT, Better.HIGHER, c.top10(), p.top10()),
					line("Ranking 11–100", Unit.COUNT, Better.NEITHER, c.ranking(), p.ranking()),
					line("Not ranked", Unit.COUNT, Better.LOWER, c.notRanked(), p.notRanked()),
					line("Improved keywords", Unit.COUNT, Better.HIGHER, c.improved(), p.improved()),
					line("Declined keywords", Unit.COUNT, Better.LOWER, c.declined(), p.declined()),
					line("Average position", Unit.DECIMAL, Better.LOWER, c.averagePosition(), p.averagePosition()))));
		}
		if (d.leads() != null) {
			List<Line> lines = new ArrayList<>();
			lines.add(line("Leads generated", Unit.COUNT, Better.HIGHER, d.leads().total(), d.leads().comparisonTotal()));
			d.leads().bySource().forEach(s -> lines.add(line(sourceLabel(s.source()) + " leads", Unit.COUNT, Better.HIGHER,
					s.leads(), s.comparison())));
			addTargetLines(lines, "Monthly lead target", targetsNow.get(ActualSource.LEADS), targetsBefore.get(ActualSource.LEADS));
			groups.add(new Group("LEADS", "Lead performance", lines));
		}
		if (d.email() != null) {
			var c = d.email().current();
			var p = d.email().comparison();
			groups.add(new Group("EMAIL", "Email campaign performance", List.of(
					line("Campaigns sent", Unit.COUNT, Better.NEITHER, c.campaigns(), p.campaigns()),
					line("Emails sent", Unit.COUNT, Better.NEITHER, c.counts().emailsSent(), p.counts().emailsSent()),
					line("Delivered", Unit.COUNT, Better.HIGHER, c.counts().delivered(), p.counts().delivered()),
					line("Unique opens", Unit.COUNT, Better.HIGHER, c.counts().uniqueOpens(), p.counts().uniqueOpens()),
					line("Unique clicks", Unit.COUNT, Better.HIGHER, c.counts().uniqueClicks(), p.counts().uniqueClicks()),
					line("Leads", Unit.COUNT, Better.HIGHER, c.counts().leads(), p.counts().leads()),
					line("Open rate", Unit.PERCENT, Better.HIGHER, c.rates().openRate(), p.rates().openRate()),
					line("Click rate", Unit.PERCENT, Better.HIGHER, c.rates().clickRate(), p.rates().clickRate()),
					line("Lead conversion", Unit.PERCENT, Better.HIGHER, c.rates().leadConversionRate(), p.rates().leadConversionRate()))));
		}
		if (d.linkedin() != null) {
			var c = d.linkedin().current();
			var p = d.linkedin().comparison();
			groups.add(new Group("LINKEDIN", "LinkedIn campaign performance", List.of(
					line("Campaigns with results", Unit.COUNT, Better.NEITHER, c.campaigns(), p.campaigns()),
					line("Spend", Unit.CURRENCY, Better.NEITHER, c.results().spend(), p.results().spend()),
					line("Impressions", Unit.COUNT, Better.HIGHER, c.results().impressions(), p.results().impressions()),
					line("Clicks", Unit.COUNT, Better.HIGHER, c.results().clicks(), p.results().clicks()),
					line("CTR", Unit.PERCENT, Better.HIGHER, c.rates().ctr(), p.rates().ctr()),
					line("Leads", Unit.COUNT, Better.HIGHER, c.results().leads(), p.results().leads()),
					line("Cost per lead", Unit.CURRENCY, Better.LOWER, c.rates().costPerLead(), p.rates().costPerLead()),
					line("Conversion rate", Unit.PERCENT, Better.HIGHER, c.rates().conversionRate(), p.rates().conversionRate()))));
		}
		if (d.backlinks() != null) {
			var c = d.backlinks().current();
			var p = d.backlinks().comparison();
			List<Line> lines = new ArrayList<>();
			TargetItem now = targetsNow.get(ActualSource.BACKLINKS_LIVE);
			TargetItem before = targetsBefore.get(ActualSource.BACKLINKS_LIVE);
			if (now != null || before != null) {
				lines.add(targetLine("Backlink target", Unit.COUNT, Better.NEITHER, value(now), value(before)));
			}
			lines.add(line("Submitted", Unit.COUNT, Better.HIGHER, c.submitted(), p.submitted()));
			lines.add(line("Approved", Unit.COUNT, Better.HIGHER, c.approved(), p.approved()));
			lines.add(line("Live", Unit.COUNT, Better.HIGHER, c.live(), p.live()));
			if (now != null || before != null) {
				lines.add(targetLine("Still to submit", Unit.COUNT, Better.LOWER, remaining(now, c.submitted()),
						remaining(before, p.submitted())));
			}
			lines.add(line("Rejected", Unit.COUNT, Better.LOWER, c.rejected(), p.rejected()));
			lines.add(line("Lost", Unit.COUNT, Better.LOWER, c.lost(), p.lost()));
			groups.add(new Group("BACKLINKS", "Backlink performance", lines));
		}
		if (d.content() != null) {
			var c = d.content().current();
			var p = d.content().comparison();
			List<Line> lines = new ArrayList<>();
			TargetItem now = targetsNow.get(ActualSource.BLOGS_PUBLISHED);
			TargetItem before = targetsBefore.get(ActualSource.BLOGS_PUBLISHED);
			if (now != null || before != null) {
				lines.add(targetLine("Blog target", Unit.COUNT, Better.NEITHER, value(now), value(before)));
			}
			lines.add(line("Blogs planned", Unit.COUNT, Better.NEITHER, c.plannedBlogs(), p.plannedBlogs()));
			lines.add(line("Blogs published", Unit.COUNT, Better.HIGHER, c.publishedBlogs(), p.publishedBlogs()));
			if (now != null || before != null) {
				lines.add(targetLine("Blogs remaining", Unit.COUNT, Better.LOWER, remaining(now, c.publishedBlogs()),
						remaining(before, p.publishedBlogs())));
			}
			lines.add(line("All content published", Unit.COUNT, Better.HIGHER, c.publishedAll(), p.publishedAll()));
			lines.add(line("Content refreshed", Unit.COUNT, Better.NEITHER, c.refreshed(), p.refreshed()));
			lines.add(line("Leads from content", Unit.COUNT, Better.HIGHER, c.leads(), p.leads()));
			groups.add(new Group("CONTENT", "Content performance", lines));
		}
		var a = d.activities();
		ActivityQuery.MonthCounts b = activityQuery.month(previous, ownerId, calendar.today());
		groups.add(new Group("ACTIVITIES", "Recurring activity completion", List.of(
				line("Occurrences due", Unit.COUNT, Better.NEITHER, a.due(), b.due()),
				line("Completed", Unit.COUNT, Better.HIGHER, a.completed(), b.completed()),
				line("Skipped", Unit.COUNT, Better.LOWER, a.skipped(), b.skipped()),
				line("Overdue", Unit.COUNT, Better.LOWER, a.overdue(), b.overdue()),
				line("Completion", Unit.PERCENT, Better.HIGHER, a.completionPct(),
						MarketingMath.percent(b.completed(), b.due() - b.skipped())))));

		KeywordMovements movements = d.seo() == null ? null
				: new KeywordMovements(moves(period, ownerId, Movement.IMPROVED, "improvement"),
						moves(period, ownerId, Movement.DECLINED, "decline"));
		List<TargetRow> targets = d.targets() == null ? null
				: d.targets().targets().stream().map(t -> {
					TargetItem p = previousByType.get(t.type().id());
					return new TargetRow(t.type().name(), t.type().unit().name(), t.targetValue(), t.actual(),
							t.achievementPct(), t.remaining(), t.status(), p == null ? null : p.targetValue(),
							p == null ? null : p.actual(), p == null ? null : p.achievementPct());
				}).toList();
		return new MonthlyReport(d.period(), d.comparisonPeriod(), ownerId, false, calendar.now(), null, groups,
				movements, targets);
	}

	/** The biggest moves of the month in one direction (the SEO ranking table, sorted by the move). */
	private List<KeywordMove> moves(MarketingPeriod period, Long ownerId, Movement movement, String sort) {
		SeoRankingTableQuery.Filter filter = new SeoRankingTableQuery.Filter(null, null, ownerId, null, null, null, null,
				null, movement);
		return rankingService.table(filter, period, sort, 0, KEYWORD_MOVES)
			.content()
			.stream()
			.map((KeywordItem k) -> new KeywordMove(k.id(), k.keyword(), k.page() == null ? null : k.page().title(),
					k.ranking().previousPosition(), k.ranking().position(),
					k.ranking().change() == null ? null : k.ranking().change().value()))
			.toList();
	}

	/** Without a permission, its groups, target lines, keyword movements and target table are taken out. */
	MonthlyReport visibleTo(MonthlyReport report, AuthenticatedUser viewer) {
		boolean targets = viewer.hasPermission("TARGET_VIEW");
		List<Group> groups = report.groups()
			.stream()
			.filter(g -> viewer.hasPermission(GROUP_PERMISSIONS.getOrDefault(g.key(), "MARKETING_VIEW")))
			.map(g -> targets ? g : new Group(g.key(), g.title(), g.lines().stream().filter(l -> !l.target()).toList()))
			.toList();
		return new MonthlyReport(report.period(), report.comparisonPeriod(), report.ownerId(), report.frozen(),
				report.generatedAt(), report.generatedBy(), groups,
				viewer.hasPermission("SEO_VIEW") ? report.keywordMovements() : null, targets ? report.targets() : null);
	}

	private MonthlyReport read(MarketingMonthlyReport frozen) {
		MonthlyReport stored = objectMapper.readValue(frozen.getPayload(), MonthlyReport.class);
		return new MonthlyReport(stored.period(), stored.comparisonPeriod(), null, true, frozen.getGeneratedAt(),
				UserSummary.of(frozen.getGeneratedBy()), stored.groups(), stored.keywordMovements(), stored.targets());
	}

	// --- lines ----------------------------------------------------------------------------------------------

	static Line line(String label, Unit unit, Better better, Number current, Number previous) {
		return build(label, unit, better, current, previous, false);
	}

	static Line targetLine(String label, Unit unit, Better better, Number current, Number previous) {
		return build(label, unit, better, current, previous, true);
	}

	private static Line build(String label, Unit unit, Better better, Number current, Number previous, boolean target) {
		BigDecimal c = decimal(current);
		BigDecimal p = decimal(previous);
		BigDecimal change = c == null || p == null ? null : c.subtract(p);
		BigDecimal changePct = unit == Unit.PERCENT || change == null || p.signum() == 0 ? null
				: MarketingMath.percent(change, p.abs());
		return new Line(label, unit, better, c, p, change, changePct, target);
	}

	private static void addTargetLines(List<Line> lines, String label, TargetItem now, TargetItem before) {
		if (now == null && before == null) {
			return;
		}
		lines.add(targetLine(label, Unit.COUNT, Better.NEITHER, value(now), value(before)));
		lines.add(targetLine("Achievement", Unit.PERCENT, Better.HIGHER, now == null ? null : now.achievementPct(),
				before == null ? null : before.achievementPct()));
		lines.add(targetLine("Remaining", Unit.COUNT, Better.LOWER, now == null ? null : now.remaining(),
				before == null ? null : before.remaining()));
	}

	private static BigDecimal value(TargetItem target) {
		return target == null ? null : target.targetValue();
	}

	private static BigDecimal remaining(TargetItem target, long done) {
		return target == null ? null : MarketingMath.remaining(target.targetValue(), done);
	}

	private static BigDecimal decimal(Number value) {
		if (value == null) {
			return null;
		}
		return value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
	}

	private static String sourceLabel(LeadSource source) {
		return switch (source) {
			case ORGANIC -> "Organic";
			case EMAIL -> "Email";
			case LINKEDIN -> "LinkedIn";
			case PAID_CAMPAIGN -> "Paid campaign";
			case BLOG -> "Blog";
			case WEBSITE -> "Website";
			case REFERRAL -> "Referral";
			case OTHER -> "Other";
		};
	}

}
