package com.teamops.marketing.seo.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.teamops.marketing.seo.service.SeoService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** SEO endpoints: SEO_VIEW reads, SEO_EDIT writes, and everything sits behind MARKETING_VIEW. */
@WebMvcTest(controllers = { SeoPageController.class, SeoKeywordController.class })
@SecuritySliceTest
class SeoApiSecurityTest {

	/** Reads SEO only (e.g. the content writer in the dev seed). */
	private static final AuthenticatedUser SEO_READER = new AuthenticatedUser(13L, "kavya.suresh@teamops.local",
			"Kavya Suresh", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW"));

	private static final AuthenticatedUser SEO_EDITOR = new AuthenticatedUser(12L, "arun.kumar@teamops.local",
			"Arun Kumar", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT"));

	/** Has the marketing module but not SEO (e.g. a campaign manager). */
	private static final AuthenticatedUser CAMPAIGNS_ONLY = new AuthenticatedUser(14L, "campaigns@teamops.local",
			"Campaigns", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CAMPAIGN_VIEW"));

	private static final String VALID_PAGE = """
			{"url":"/services/sap-testing","title":"SAP Testing Services","pageType":"SERVICE","departmentId":7}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private SeoService seoService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void usersWithoutSeoAccessAreForbidden() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, CAMPAIGNS_ONLY)) {
			for (String path : List.of("/api/marketing/pages", "/api/marketing/pages/options", "/api/marketing/pages/1",
					"/api/marketing/keywords", "/api/marketing/keywords/1")) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user)))
					.andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(seoService);
	}

	@Test
	void readersCanListButNotChange() throws Exception {
		when(seoService.period(any(), any())).thenReturn(new MarketingPeriod(10, 2026));
		when(seoService.searchPages(any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(new PageResponse<>(List.of(), 0, 25, 0, 0));

		mvc.perform(get("/api/marketing/pages").param("month", "9")
			.param("year", "2026")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER))).andExpect(status().isOk());
		verify(seoService).period(9, 2026);

		mvc.perform(post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content(VALID_PAGE)
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER))).andExpect(status().isForbidden());
		mvc.perform(post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content("{\"pageId\":1,\"keyword\":\"sap\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/marketing/keywords/1").header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER)))
			.andExpect(status().isForbidden());
	}

	@Test
	void pageInputIsValidated() throws Exception {
		mvc.perform(post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"url\":\"services page\",\"title\":\"\",\"departmentId\":7}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("url", "title", "pageType")));

		mvc.perform(post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content("{\"pageId\":1,\"keyword\":\"sap\",\"targetPosition\":101,\"keywordDifficulty\":-1}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("targetPosition", "keywordDifficulty")));
		verifyNoInteractions(seoService);
	}

	@Test
	void editorsCreatePagesAndGetCreated() throws Exception {
		mvc.perform(post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content(VALID_PAGE)
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_EDITOR))).andExpect(status().isCreated());
		verify(seoService).createPage(any(), eq(SEO_EDITOR), any());

		mvc.perform(delete("/api/marketing/pages/5").header(HttpHeaders.AUTHORIZATION, bearer(SEO_EDITOR)))
			.andExpect(status().isNoContent());
		verify(seoService).deletePage(eq(5L), eq(SEO_EDITOR), any());
	}

	@Test
	void unknownSortFieldsAreRejected() throws Exception {
		mvc.perform(get("/api/marketing/keywords").param("sort", "current_position,asc")
			.header(HttpHeaders.AUTHORIZATION, bearer(SEO_READER)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SORT"));
	}

}
