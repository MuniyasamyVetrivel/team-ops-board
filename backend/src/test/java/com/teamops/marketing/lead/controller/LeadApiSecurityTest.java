package com.teamops.marketing.lead.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.teamops.marketing.lead.repository.LeadQuery.LinkKind;
import com.teamops.marketing.lead.service.LeadService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Marketing leads: LEAD_VIEW reads, LEAD_EDIT changes, all behind MARKETING_VIEW. Service mocked. */
@WebMvcTest(controllers = LeadController.class)
@SecuritySliceTest
class LeadApiSecurityTest {

	private static final AuthenticatedUser VIEWER = new AuthenticatedUser(12L, "arun.kumar@teamops.local", "Arun Kumar",
			7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "LEAD_VIEW"));

	private static final AuthenticatedUser EDITOR = new AuthenticatedUser(5L, "priya.menon@teamops.local", "Priya Menon",
			7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "LEAD_VIEW", "LEAD_EDIT"));

	/** Marketing access without leads. */
	private static final AuthenticatedUser CAMPAIGNS_ONLY = new AuthenticatedUser(13L, "campaigns@teamops.local",
			"Campaigns Only", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CAMPAIGN_VIEW"));

	private static final String VALID = """
			{"name":"Anita Rao","company":"Acme Manufacturing","email":"anita.rao@acme.example","source":"ORGANIC",
			 "leadDate":"2026-10-06"}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private LeadService leadService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void leadsNeedLeadView() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, CAMPAIGNS_ONLY)) {
			for (String path : List.of("/api/marketing/leads", "/api/marketing/leads/1", "/api/marketing/leads/summary",
					"/api/marketing/leads/trend", "/api/marketing/leads/export",
					"/api/marketing/leads/link-options?kind=EMAIL_CAMPAIGN")) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(leadService);
	}

	@Test
	void viewersCannotChangeLeads() throws Exception {
		mvc.perform(post("/api/marketing/leads").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(put("/api/marketing/leads/1/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"CONVERTED\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/marketing/leads/1").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(leadService);

		mvc.perform(post("/api/marketing/leads").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isCreated());
		verify(leadService).create(any(), eq(EDITOR), any());
	}

	@Test
	void leadInputIsValidated() throws Exception {
		mvc.perform(post("/api/marketing/leads").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\",\"email\":\"not-an-email\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("name", "email", "source", "leadDate")));
		mvc.perform(put("/api/marketing/leads/1/status").contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("version", "status")));
		verifyNoInteractions(leadService);
	}

	@Test
	void summaryComparesWithThePreviousMonthUnlessToldOtherwise() throws Exception {
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(leadService.period(10, 2026)).thenReturn(october);

		mvc.perform(get("/api/marketing/leads/summary").param("month", "10")
			.param("year", "2026")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(leadService).summary(october, new MarketingPeriod(9, 2026), null, VIEWER);

		mvc.perform(get("/api/marketing/leads/summary").param("month", "10")
			.param("year", "2026")
			.param("compareYear", "2025")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(leadService).summary(october, new MarketingPeriod(10, 2025), null, VIEWER);
	}

	@Test
	void linkOptionsNeedAKind() throws Exception {
		mvc.perform(get("/api/marketing/leads/link-options").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/marketing/leads/link-options").param("kind", "CONTENT")
			.param("search", "sap")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(leadService).linkOptions(LinkKind.CONTENT, "sap");
	}

	@Test
	void exportIsAnAttachment() throws Exception {
		when(leadService.export(any())).thenReturn("Lead ID\r\n".getBytes());

		mvc.perform(get("/api/marketing/leads/export").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("marketing-leads.csv")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

}
