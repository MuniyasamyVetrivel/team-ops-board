package com.teamops.marketing.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.report.dto.MarketingReportDtos.Better;
import com.teamops.marketing.report.dto.MarketingReportDtos.Group;
import com.teamops.marketing.report.dto.MarketingReportDtos.KeywordMovements;
import com.teamops.marketing.report.dto.MarketingReportDtos.Line;
import com.teamops.marketing.report.dto.MarketingReportDtos.MonthlyReport;
import com.teamops.marketing.report.dto.MarketingReportDtos.TargetRow;
import com.teamops.marketing.report.dto.MarketingReportDtos.Unit;

/** The monthly report's comparison figures and how a stored report is narrowed to its reader. */
class MarketingReportServiceTest {

	@Test
	void briefSection50ComparesWithTheMonthBefore() {
		// September → October: leads 180 → 200, +11.11%.
		Line leads = MarketingReportService.line("Leads generated", Unit.COUNT, Better.HIGHER, 200, 180);
		assertThat(leads.change()).isEqualByComparingTo("20");
		assertThat(leads.changePct()).isEqualByComparingTo("11.11");
		// Top 10 keywords 35 → 42: +20%. Backlinks 18 → 22: +22.22%.
		assertThat(MarketingReportService.line("Top 10", Unit.COUNT, Better.HIGHER, 42, 35).changePct()).isEqualByComparingTo("20");
		assertThat(MarketingReportService.line("Live", Unit.COUNT, Better.HIGHER, 22, 18).changePct()).isEqualByComparingTo("22.22");
		assertThat(MarketingReportService.line("Lost", Unit.COUNT, Better.LOWER, 1, 4).changePct()).isEqualByComparingTo("-75");
	}

	@Test
	void ratesChangeByPointsAndNothingIsDividedByZero() {
		Line openRate = MarketingReportService.line("Open rate", Unit.PERCENT, Better.HIGHER, new BigDecimal("35.42"),
				new BigDecimal("31.58"));
		assertThat(openRate.change()).isEqualByComparingTo("3.84");
		assertThat(openRate.changePct()).isNull();
		Line fromZero = MarketingReportService.line("Leads", Unit.COUNT, Better.HIGHER, 5, 0);
		assertThat(fromZero.change()).isEqualByComparingTo("5");
		assertThat(fromZero.changePct()).isNull();
		Line missing = MarketingReportService.line("Average position", Unit.DECIMAL, Better.LOWER, new BigDecimal("9.4"), null);
		assertThat(missing.change()).isNull();
		assertThat(missing.changePct()).isNull();
		assertThat(MarketingReportService.targetLine("Blog target", Unit.COUNT, Better.NEITHER, 12, 12).target()).isTrue();
	}

	@Test
	void aStoredReportOnlyShowsWhatTheReaderMaySee() {
		MonthlyReport full = new MonthlyReport(new Period(9, 2026, "September 2026"), new Period(8, 2026, "August 2026"), null,
				true, Instant.parse("2026-10-01T00:00:00Z"), null,
				List.of(new Group("SEO", "SEO summary", List.of(MarketingReportService.line("Top 10", Unit.COUNT, Better.HIGHER, 5, 4))),
						new Group("LEADS", "Lead performance", List.of(
								MarketingReportService.line("Leads generated", Unit.COUNT, Better.HIGHER, 210, 185),
								MarketingReportService.targetLine("Monthly lead target", Unit.COUNT, Better.NEITHER, 220, 200))),
						new Group("ACTIVITIES", "Recurring activity completion",
								List.of(MarketingReportService.line("Completed", Unit.COUNT, Better.HIGHER, 4, 5)))),
				new KeywordMovements(List.of(), List.of()),
				List.of(new TargetRow("Website Leads", "COUNT", null, null, null, null, null, null, null, null)));
		AuthenticatedUser leadsOnly = new AuthenticatedUser(9L, "x@teamops.local", "X", 7L, Set.of("EMPLOYEE"),
				Set.of("MARKETING_VIEW", "LEAD_VIEW"));

		MonthlyReport shown = new MarketingReportService(null, null, null, null, null, null, null, null, null)
			.visibleTo(full, leadsOnly);

		assertThat(shown.groups()).extracting(Group::key).containsExactly("LEADS", "ACTIVITIES");
		assertThat(shown.groups().getFirst().lines()).extracting(Line::label).containsExactly("Leads generated");
		assertThat(shown.keywordMovements()).isNull();
		assertThat(shown.targets()).isNull();
		assertThat(shown.frozen()).isTrue();
	}

}
