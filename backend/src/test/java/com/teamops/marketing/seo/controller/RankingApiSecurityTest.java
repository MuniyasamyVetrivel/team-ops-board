package com.teamops.marketing.seo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.RankingChange.Movement;
import com.teamops.marketing.seo.repository.SeoRankingTableQuery.Filter;
import com.teamops.marketing.seo.repository.SeoRankingTableQuery.StandingFilter;
import com.teamops.marketing.seo.service.KeywordRankingService;
import com.teamops.marketing.seo.service.SeoService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Ranking endpoints: SEO_VIEW reads and exports, SEO_EDIT records and corrects. Services are mocked. */
@WebMvcTest(controllers = SeoRankingController.class)
@SecuritySliceTest
class RankingApiSecurityTest {

	private static final AuthenticatedUser SEO_READER = new AuthenticatedUser(13L, "kavya.suresh@teamops.local",
			"Kavya Suresh", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW"));

	private static final AuthenticatedUser SEO_EDITOR = new AuthenticatedUser(12L, "arun.kumar@teamops.local",
			"Arun Kumar", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private KeywordRankingService rankingService;

	@MockitoBean
	private SeoService seoService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void usersWithoutSeoAccessAreForbidden() throws Exception {
		for (String path : List.of("/api/marketing/rankings", "/api/marketing/rankings/monthly",
				"/api/marketing/rankings/export", "/api/marketing/keywords/1/rankings",
				"/api/marketing/pages/1/rankings")) {
			mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
				.andExpect(status().isForbidden());
		}
		verifyNoInteractions(rankingService);
	}

	@Test
	void readersCannotRecordOrCorrect() throws Exception {
		mvc.perform(post("/api/marketing/keywords/1/rankings").contentType(MediaType.APPLICATION_JSON)
			.content("{\"month\":10,\"year\":2026,\"position\":7}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER))).andExpect(status().isForbidden());
		mvc.perform(post("/api/marketing/rankings/monthly").contentType(MediaType.APPLICATION_JSON)
			.content("{\"month\":10,\"year\":2026,\"entries\":[{\"keywordId\":1,\"position\":7}]}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER))).andExpect(status().isForbidden());
		mvc.perform(put("/api/marketing/rankings/5").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"position\":7}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER))).andExpect(status().isForbidden());
		verifyNoInteractions(rankingService);
	}

	@Test
	void tableFiltersReachTheService() throws Exception {
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(seoService.period(10, 2026)).thenReturn(october);
		when(rankingService.table(any(), any(), any(), eq(0), eq(25))).thenReturn(new PageResponse<>(List.of(), 0, 25, 0, 0));

		mvc.perform(get("/api/marketing/rankings").param("month", "10")
			.param("year", "2026")
			.param("standing", "TOP_10")
			.param("movement", "IMPROVED")
			.param("minPosition", "1")
			.param("maxPosition", "3")
			.param("sort", "improvement")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER))).andExpect(status().isOk());
		verify(rankingService).table(eq(new Filter(null, null, null, null, null, StandingFilter.TOP_10, 1, 3,
				Movement.IMPROVED)), eq(october), eq("improvement"), eq(0), eq(25));

		mvc.perform(get("/api/marketing/rankings").param("maxPosition", "101")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER))).andExpect(status().isBadRequest());
	}

	@Test
	void exportIsAnAttachmentReadersMayDownload() throws Exception {
		when(seoService.period(any(), any())).thenReturn(new MarketingPeriod(9, 2026));
		when(rankingService.export(any(), any(), any())).thenReturn("Page\r\n".getBytes());

		mvc.perform(get("/api/marketing/rankings/export").header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("seo-rankings-2026-09.csv")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void rankingInputIsValidated() throws Exception {
		mvc.perform(post("/api/marketing/keywords/1/rankings").contentType(MediaType.APPLICATION_JSON)
			.content("{\"month\":13,\"position\":101}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("month", "year", "position")));
		mvc.perform(post("/api/marketing/rankings/monthly").contentType(MediaType.APPLICATION_JSON)
			.content("{\"month\":10,\"year\":2026,\"entries\":[]}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("entries")));
		mvc.perform(put("/api/marketing/rankings/5").contentType(MediaType.APPLICATION_JSON)
			.content("{\"position\":7}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("version")));
		verifyNoInteractions(rankingService);
	}

	@Test
	void editorsRecordAndGetCreated() throws Exception {
		mvc.perform(post("/api/marketing/rankings/monthly").contentType(MediaType.APPLICATION_JSON)
			.content("{\"month\":10,\"year\":2026,\"entries\":[{\"keywordId\":1,\"position\":null}]}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_EDITOR))).andExpect(status().isCreated());
		verify(rankingService).recordMonthly(any(), eq(SEO_EDITOR), any());
	}

}
