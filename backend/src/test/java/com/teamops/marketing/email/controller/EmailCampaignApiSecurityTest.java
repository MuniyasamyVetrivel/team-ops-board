package com.teamops.marketing.email.controller;

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
import com.teamops.marketing.email.service.EmailCampaignService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Email campaigns: CAMPAIGN_VIEW reads, CAMPAIGN_EDIT changes, all behind MARKETING_VIEW. Service mocked. */
@WebMvcTest(controllers = EmailCampaignController.class)
@SecuritySliceTest
class EmailCampaignApiSecurityTest {

	private static final AuthenticatedUser VIEWER = new AuthenticatedUser(12L, "arun.kumar@teamops.local", "Arun Kumar",
			7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CAMPAIGN_VIEW"));

	private static final AuthenticatedUser EDITOR = new AuthenticatedUser(5L, "priya.menon@teamops.local", "Priya Menon",
			7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CAMPAIGN_VIEW", "CAMPAIGN_EDIT"));

	/** Marketing access without campaigns. */
	private static final AuthenticatedUser SEO_ONLY = new AuthenticatedUser(13L, "seo@teamops.local", "Seo Only", 7L,
			Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW"));

	private static final String VALID = """
			{"name":"SAP Testing Services Outreach","campaignType":"LEAD_GENERATION","campaignDate":"2026-10-02",
			 "status":"SENT","emailsSent":25000,"delivered":24000,"uniqueOpens":8500,"opened":9400}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private EmailCampaignService campaignService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void campaignsNeedCampaignView() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SEO_ONLY)) {
			for (String path : List.of("/api/marketing/email-campaigns", "/api/marketing/email-campaigns/1",
					"/api/marketing/email-campaigns/summary", "/api/marketing/email-campaigns/trend",
					"/api/marketing/email-campaigns/export")) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(campaignService);
	}

	@Test
	void viewersCannotChangeCampaigns() throws Exception {
		mvc.perform(post("/api/marketing/email-campaigns").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/marketing/email-campaigns/1").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(campaignService);

		mvc.perform(post("/api/marketing/email-campaigns").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isCreated());
		verify(campaignService).create(any(), eq(EDITOR), any());
	}

	@Test
	void campaignInputIsValidated() throws Exception {
		mvc.perform(post("/api/marketing/email-campaigns").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\",\"emailsSent\":-1,\"uniqueOpens\":200000000}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field",
					hasItems("name", "campaignType", "campaignDate", "status", "emailsSent", "uniqueOpens")));
		verifyNoInteractions(campaignService);
	}

	@Test
	void summaryComparesWithThePreviousMonthUnlessToldOtherwise() throws Exception {
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(campaignService.period(10, 2026)).thenReturn(october);

		mvc.perform(get("/api/marketing/email-campaigns/summary").param("month", "10")
			.param("year", "2026")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(campaignService).summary(october, new MarketingPeriod(9, 2026), null);

		mvc.perform(get("/api/marketing/email-campaigns/summary").param("month", "10")
			.param("year", "2026")
			.param("compareYear", "2025")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(campaignService).summary(october, new MarketingPeriod(10, 2025), null);
	}

	@Test
	void exportIsAnAttachment() throws Exception {
		when(campaignService.export(any(), any(), any(), any(), isNull())).thenReturn("Name\r\n".getBytes());

		mvc.perform(get("/api/marketing/email-campaigns/export").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("email-campaigns.csv")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

}
