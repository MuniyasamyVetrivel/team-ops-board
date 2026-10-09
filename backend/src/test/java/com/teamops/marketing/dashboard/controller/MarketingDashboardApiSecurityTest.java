package com.teamops.marketing.dashboard.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.dashboard.service.MarketingDashboardService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** The marketing dashboard needs MARKETING_VIEW; the service decides each section. Service mocked. */
@WebMvcTest(controllers = MarketingDashboardController.class)
@SecuritySliceTest
class MarketingDashboardApiSecurityTest {

	private static final AuthenticatedUser MARKETER = new AuthenticatedUser(12L, "arun.kumar@teamops.local", "Arun Kumar",
			7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private MarketingDashboardService dashboardService;

	@Test
	void theDashboardNeedsMarketingView() throws Exception {
		mvc.perform(get("/api/marketing/dashboard")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/marketing/dashboard")
			.header(HttpHeaders.AUTHORIZATION, SliceAuth.bearer(jwtTokenService, userPrincipalService, SliceAuth.EMPLOYEE)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(dashboardService);
	}

	@Test
	void monthOwnerAndTrendLengthArePassedOn() throws Exception {
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(dashboardService.period(10, 2026)).thenReturn(october);

		mvc.perform(get("/api/marketing/dashboard").param("month", "10")
			.param("year", "2026")
			.param("ownerId", "5")
			.param("months", "12")
			.header(HttpHeaders.AUTHORIZATION, SliceAuth.bearer(jwtTokenService, userPrincipalService, MARKETER)))
			.andExpect(status().isOk());
		verify(dashboardService).dashboard(october, 5L, 12, MARKETER);

		mvc.perform(get("/api/marketing/dashboard")
			.header(HttpHeaders.AUTHORIZATION, SliceAuth.bearer(jwtTokenService, userPrincipalService, MARKETER)))
			.andExpect(status().isOk());
		verify(dashboardService).period(null, null);
		verify(dashboardService).dashboard(null, null, null, MARKETER);
	}

}
