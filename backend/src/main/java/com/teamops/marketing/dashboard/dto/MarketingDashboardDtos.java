package com.teamops.marketing.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.teamops.marketing.activity.dto.ActivityDtos.OccurrenceItem;
import com.teamops.marketing.backlink.dto.BacklinkDtos.MonthActivity;
import com.teamops.marketing.backlink.dto.BacklinkDtos.MonthTarget;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.content.dto.ContentDtos.BlogTarget;
import com.teamops.marketing.content.dto.ContentDtos.MonthFigures;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.email.service.EmailCounts;
import com.teamops.marketing.email.service.EmailRates;
import com.teamops.marketing.paid.service.BudgetProgress;
import com.teamops.marketing.paid.service.PaidRates;
import com.teamops.marketing.paid.service.PaidResults;
import com.teamops.marketing.seo.service.SeoStats;
import com.teamops.marketing.target.dto.TargetDtos.StatusSummary;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;

/**
 * The Digital Marketing executive dashboard (brief sections 22, 49 and 61). Every figure is computed per request;
 * a section is null when the viewer lacks its module's view permission, and targets are null without TARGET_VIEW.
 */
public final class MarketingDashboardDtos {

	private MarketingDashboardDtos() {
	}

	/** SEO for the month against the month before: pages, keywords, top 3/10, movement, not ranked, average position. */
	public record SeoSection(long totalPages, SeoStats current, SeoStats comparison) {

	}

	public record SourceCount(LeadSource source, long leads, long comparison) {

	}

	/** Leads by source; {@code target} is the Website Leads target (achievement %, remaining, status). */
	public record LeadSection(long total, long comparisonTotal, List<SourceCount> bySource, TargetItem target) {

	}

	public record EmailMonth(int campaigns, EmailCounts counts, EmailRates rates) {

	}

	/** Sent email campaigns of the month; rates come from the month's totals, never averaged. */
	public record EmailSection(EmailMonth current, EmailMonth comparison) {

	}

	public record PaidMonth(int campaigns, PaidResults results, PaidRates rates) {

	}

	/** LinkedIn campaigns: the month's results and the running budget of the campaigns live in it. */
	public record LinkedInSection(PaidMonth current, PaidMonth comparison, int runningCampaigns, BudgetProgress budget) {

	}

	/** Each stage in the month of its own date; {@code target.remaining} is what is still to submit. */
	public record BacklinkSection(MonthActivity current, MonthActivity comparison, MonthTarget target) {

	}

	/** Blogs planned and published, and leads naming content; {@code blogTarget.remaining} = target − published. */
	public record ContentSection(MonthFigures current, MonthFigures comparison, BlogTarget blogTarget) {

	}

	/** Every target of the month (for the owner when filtered) with its computed status, and the status counts. */
	public record TargetSection(List<TargetItem> targets, StatusSummary summary) {

	}

	/**
	 * Recurring activity occurrences due in the month. {@code completionPct} = completed ÷ (due − skipped);
	 * {@code attention} lists the open occurrences due by the end of the month, the oldest first.
	 */
	public record ActivitySection(long due, long completed, long skipped, long open, long overdue,
			BigDecimal completionPct, List<OccurrenceItem> attention) {

	}

	/**
	 * One month of the trend. A field is null when the viewer may not see its module; {@code top10Keywords} is also
	 * null for a month with no rankings recorded.
	 */
	public record TrendMonth(Period period, Long leads, Long top10Keywords, Long emailLeads, BigDecimal linkedinSpend,
			Long linkedinLeads, Long backlinksLive, Long blogsPublished) {

	}

	public record Dashboard(Period period, Period comparisonPeriod, LocalDate today, boolean targetsVisible,
			SeoSection seo, LeadSection leads, EmailSection email, LinkedInSection linkedin, BacklinkSection backlinks,
			ContentSection content, TargetSection targets, ActivitySection activities, List<TrendMonth> trend) {

	}

}
