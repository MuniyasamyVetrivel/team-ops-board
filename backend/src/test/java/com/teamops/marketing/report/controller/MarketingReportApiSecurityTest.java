package com.teamops.marketing.report.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.report.CsvReportExporter;
import com.teamops.common.report.ReportExporters;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.report.dto.MarketingReportDtos.MonthlyReport;
import com.teamops.marketing.report.service.MarketingReportService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** The marketing monthly report: MARKETING_VIEW reads and exports, MARKETING_EDIT freezes. Service mocked. */
@WebMvcTest(controllers = MarketingReportController.class)
@SecuritySliceTest
@Import({ ReportExporters.class, CsvReportExporter.class })
class MarketingReportApiSecurityTest {

	private static final AuthenticatedUser MARKETER = new AuthenticatedUser(12L, "arun.kumar@teamops.local", "Arun Kumar",
			7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW"));

	private static final AuthenticatedUser MANAGER = new AuthenticatedUser(5L, "priya.menon@teamops.local", "Priya Menon",
			7L, Set.of("DEPARTMENT_MANAGER"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "MARKETING_EDIT"));

	private static final MarketingPeriod SEPTEMBER = new MarketingPeriod(9, 2026);

	private static final MonthlyReport REPORT = new MonthlyReport(new Period(9, 2026, "September 2026"),
			new Period(8, 2026, "August 2026"), null, false, Instant.parse("2026-10-09T05:00:00Z"), null, List.of(), null, null);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private MarketingReportService reportService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void theReportNeedsMarketingViewAndFreezingNeedsMarketingEdit() throws Exception {
		for (String path : List.of("/api/marketing/reports/monthly", "/api/marketing/reports/monthly/export",
				"/api/marketing/reports/frozen")) {
			mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		}
		mvc.perform(post("/api/marketing/reports/monthly/2026/9/freeze").header(HttpHeaders.AUTHORIZATION, bearer(MARKETER)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(reportService);

		when(reportService.period(9, 2026)).thenReturn(SEPTEMBER);
		mvc.perform(post("/api/marketing/reports/monthly/2026/9/freeze").header(HttpHeaders.AUTHORIZATION, bearer(MANAGER)))
			.andExpect(status().isOk());
		verify(reportService).freeze(eq(SEPTEMBER), eq(MANAGER), any());
	}

	@Test
	void monthOwnerAndLiveArePassedOnAndTheExportIsAnAttachment() throws Exception {
		when(reportService.period(9, 2026)).thenReturn(SEPTEMBER);
		when(reportService.report(SEPTEMBER, 5L, true, MARKETER)).thenReturn(REPORT);

		mvc.perform(get("/api/marketing/reports/monthly/export").param("month", "9")
			.param("year", "2026")
			.param("ownerId", "5")
			.param("live", "true")
			.header(HttpHeaders.AUTHORIZATION, bearer(MARKETER)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("marketing-report-2026-09.csv")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		verify(reportService).report(SEPTEMBER, 5L, true, MARKETER);
	}

}
