package com.teamops.marketing.backlink.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.teamops.marketing.backlink.entity.BacklinkStatus;
import com.teamops.marketing.backlink.repository.BacklinkQuery.StageKind;
import com.teamops.marketing.backlink.service.BacklinkService;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Backlinks: BACKLINK_VIEW reads, BACKLINK_EDIT changes, all behind MARKETING_VIEW. Service mocked. */
@WebMvcTest(controllers = BacklinkController.class)
@SecuritySliceTest
class BacklinkApiSecurityTest {

	private static final AuthenticatedUser VIEWER = new AuthenticatedUser(12L, "kavya.suresh@teamops.local",
			"Kavya Suresh", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "BACKLINK_VIEW"));

	private static final AuthenticatedUser EDITOR = new AuthenticatedUser(5L, "arun.kumar@teamops.local", "Arun Kumar",
			7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "BACKLINK_VIEW", "BACKLINK_EDIT"));

	/** Marketing access without backlinks. */
	private static final AuthenticatedUser LEADS_ONLY = new AuthenticatedUser(13L, "leads@teamops.local", "Leads Only",
			7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "LEAD_VIEW"));

	private static final String VALID = """
			{"targetUrl":"/services/sap-testing","linkUrl":"https://dzone.com/articles/sap","linkType":"GUEST_POST"}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private BacklinkService backlinkService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void backlinksNeedBacklinkView() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, LEADS_ONLY)) {
			for (String path : List.of("/api/marketing/backlinks", "/api/marketing/backlinks/1",
					"/api/marketing/backlinks/summary", "/api/marketing/backlinks/trend", "/api/marketing/backlinks/export")) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(backlinkService);
	}

	@Test
	void viewersCannotChangeBacklinks() throws Exception {
		mvc.perform(post("/api/marketing/backlinks").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(put("/api/marketing/backlinks/1").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(put("/api/marketing/backlinks/1/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"LIVE\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/marketing/backlinks/1").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(backlinkService);

		mvc.perform(post("/api/marketing/backlinks").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isCreated());
		verify(backlinkService).create(any(), eq(EDITOR), any());
		mvc.perform(put("/api/marketing/backlinks/1/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":2,\"status\":\"LIVE\",\"date\":\"2026-10-06\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isOk());
		verify(backlinkService).changeStatus(eq(1L),
				argThat(r -> r.version() == 2 && r.status() == BacklinkStatus.LIVE && r.date().toString().equals("2026-10-06")),
				eq(EDITOR), any());
	}

	@Test
	void backlinkInputIsValidated() throws Exception {
		mvc.perform(post("/api/marketing/backlinks").contentType(MediaType.APPLICATION_JSON)
			.content("{\"domainAuthority\":120,\"anchorText\":\"" + "x".repeat(256) + "\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("linkType", "domainAuthority", "anchorText")));
		mvc.perform(put("/api/marketing/backlinks/1/status").contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("version", "status")));
		verifyNoInteractions(backlinkService);
	}

	@Test
	void summaryComparesWithThePreviousMonthUnlessToldOtherwise() throws Exception {
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(backlinkService.period(10, 2026)).thenReturn(october);

		mvc.perform(get("/api/marketing/backlinks/summary").param("month", "10")
			.param("year", "2026")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(backlinkService).summary(october, new MarketingPeriod(9, 2026), null, VIEWER);

		mvc.perform(get("/api/marketing/backlinks/summary").param("month", "10")
			.param("year", "2026")
			.param("compareYear", "2025")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(backlinkService).summary(october, new MarketingPeriod(10, 2025), null, VIEWER);
	}

	@Test
	void theListFiltersAMonthByStage() throws Exception {
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(backlinkService.period(10, 2026)).thenReturn(october);

		mvc.perform(get("/api/marketing/backlinks").param("month", "10")
			.param("year", "2026")
			.param("stage", "LIVE")
			.param("status", "LIVE", "LOST")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(backlinkService).search(argThat(f -> october.equals(f.period()) && f.stage() == StageKind.LIVE
				&& f.statuses().equals(Set.of(BacklinkStatus.LIVE, BacklinkStatus.LOST))), any(), eq(VIEWER));
		mvc.perform(get("/api/marketing/backlinks").param("sort", "nonsense,asc")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isBadRequest());
	}

	@Test
	void exportIsAnAttachment() throws Exception {
		when(backlinkService.export(any())).thenReturn("Backlink ID\r\n".getBytes());

		mvc.perform(get("/api/marketing/backlinks/export").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("backlinks.csv")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

}
