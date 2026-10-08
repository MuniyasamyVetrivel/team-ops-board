package com.teamops.marketing.paid.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.paid.service.PaidCampaignService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Paid campaigns: CAMPAIGN_VIEW reads, CAMPAIGN_EDIT changes plans and results. Service mocked. */
@WebMvcTest(controllers = PaidCampaignController.class)
@SecuritySliceTest
class PaidCampaignApiSecurityTest {

	private static final AuthenticatedUser VIEWER = new AuthenticatedUser(12L, "arun.kumar@teamops.local", "Arun Kumar",
			7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CAMPAIGN_VIEW"));

	private static final AuthenticatedUser EDITOR = new AuthenticatedUser(5L, "priya.menon@teamops.local", "Priya Menon",
			7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CAMPAIGN_VIEW", "CAMPAIGN_EDIT"));

	private static final AuthenticatedUser SEO_ONLY = new AuthenticatedUser(13L, "seo@teamops.local", "Seo Only", 7L,
			Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW"));

	private static final String VALID = """
			{"name":"SAP S/4HANA Testing Campaign","objective":"LEAD_GENERATION","startDate":"2026-10-01",
			 "budget":50000,"status":"ACTIVE"}
			""";

	private static final String RESULTS = """
			{"amountSpent":42000,"impressions":150000,"clicks":2800,"leads":84,"conversions":21}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private PaidCampaignService campaignService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void campaignsNeedCampaignView() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SEO_ONLY)) {
			for (String path : List.of("/api/marketing/paid-campaigns", "/api/marketing/paid-campaigns/1",
					"/api/marketing/paid-campaigns/summary", "/api/marketing/paid-campaigns/trend",
					"/api/marketing/paid-campaigns/export")) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(campaignService);
	}

	@Test
	void viewersCannotChangeCampaignsOrResults() throws Exception {
		mvc.perform(post("/api/marketing/paid-campaigns").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(put("/api/marketing/paid-campaigns/1/results/2026/10").contentType(MediaType.APPLICATION_JSON)
			.content(RESULTS)
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/marketing/paid-campaigns/1").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(campaignService);

		mvc.perform(post("/api/marketing/paid-campaigns").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isCreated());
		verify(campaignService).create(any(), eq(EDITOR), any());

		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(campaignService.period(10, 2026)).thenReturn(october);
		mvc.perform(put("/api/marketing/paid-campaigns/1/results/2026/10").contentType(MediaType.APPLICATION_JSON)
			.content(RESULTS)
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isOk());
		verify(campaignService).saveMonth(eq(1L), eq(october), any(), eq(EDITOR), any());
	}

	@Test
	void inputIsValidated() throws Exception {
		mvc.perform(post("/api/marketing/paid-campaigns").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\",\"budget\":0,\"currency\":\"rupees\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field",
					hasItems("name", "objective", "startDate", "budget", "currency", "status")));
		mvc.perform(put("/api/marketing/paid-campaigns/1/results/2026/10").contentType(MediaType.APPLICATION_JSON)
			.content("{\"amountSpent\":-1,\"impressions\":-5}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field",
					hasItems("amountSpent", "impressions", "clicks", "leads", "conversions")));
		verifyNoInteractions(campaignService);
	}

	@Test
	void summaryComparesWithThePreviousMonthUnlessToldOtherwise() throws Exception {
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(campaignService.period(10, 2026)).thenReturn(october);

		mvc.perform(get("/api/marketing/paid-campaigns/summary").param("month", "10")
			.param("year", "2026")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(campaignService).summary(october, new MarketingPeriod(9, 2026), null, null);

		mvc.perform(get("/api/marketing/paid-campaigns/summary").param("month", "10")
			.param("year", "2026")
			.param("compareYear", "2025")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(campaignService).summary(october, new MarketingPeriod(10, 2025), null, null);
	}

	@Test
	void exportIsAnAttachment() throws Exception {
		when(campaignService.export(any(), any(), any(), any(), isNull())).thenReturn("Name\r\n".getBytes());

		mvc.perform(get("/api/marketing/paid-campaigns/export").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("paid-campaigns.csv")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

}
