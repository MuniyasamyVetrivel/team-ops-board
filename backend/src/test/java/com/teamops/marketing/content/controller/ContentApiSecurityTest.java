package com.teamops.marketing.content.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.content.entity.ContentType;
import com.teamops.marketing.content.service.ContentService;
import com.teamops.marketing.content.service.ContentService.DateField;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Content & Blog: CONTENT_VIEW reads, CONTENT_EDIT changes, all behind MARKETING_VIEW. Service mocked. */
@WebMvcTest(controllers = ContentController.class)
@SecuritySliceTest
class ContentApiSecurityTest {

	private static final AuthenticatedUser VIEWER = new AuthenticatedUser(12L, "arun.kumar@teamops.local", "Arun Kumar",
			7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CONTENT_VIEW"));

	private static final AuthenticatedUser EDITOR = new AuthenticatedUser(5L, "kavya.suresh@teamops.local",
			"Kavya Suresh", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CONTENT_VIEW", "CONTENT_EDIT"));

	/** Marketing access without content. */
	private static final AuthenticatedUser SEO_ONLY = new AuthenticatedUser(13L, "seo@teamops.local", "SEO Only", 7L,
			Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW"));

	private static final String VALID = """
			{"title":"SAP S/4HANA regression testing: a practical guide","contentType":"BLOG","status":"IDEA"}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private ContentService contentService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void contentNeedsContentView() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SEO_ONLY)) {
			for (String path : List.of("/api/marketing/content", "/api/marketing/content/1", "/api/marketing/content/summary",
					"/api/marketing/content/trend", "/api/marketing/content/export")) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(contentService);
	}

	@Test
	void viewersCannotChangeContent() throws Exception {
		mvc.perform(post("/api/marketing/content").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(put("/api/marketing/content/1").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(put("/api/marketing/content/1/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"PUBLISHED\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/marketing/content/1").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(contentService);

		mvc.perform(post("/api/marketing/content").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isCreated());
		verify(contentService).create(any(), eq(EDITOR), any());
	}

	@Test
	void contentInputIsValidated() throws Exception {
		mvc.perform(post("/api/marketing/content").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\" \",\"organicTraffic\":-5,\"ctaClicks\":-1}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("title", "contentType", "organicTraffic", "ctaClicks")));
		mvc.perform(put("/api/marketing/content/1/status").contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("version", "status")));
		verifyNoInteractions(contentService);
	}

	@Test
	void summaryComparesWithThePreviousMonthUnlessToldOtherwise() throws Exception {
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(contentService.period(10, 2026)).thenReturn(october);

		mvc.perform(get("/api/marketing/content/summary").param("month", "10")
			.param("year", "2026")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(contentService).summary(october, new MarketingPeriod(9, 2026), null, VIEWER);

		mvc.perform(get("/api/marketing/content/summary").param("month", "10")
			.param("year", "2026")
			.param("compareYear", "2025")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(contentService).summary(october, new MarketingPeriod(10, 2025), null, VIEWER);
	}

	@Test
	void theListFiltersAMonthByPlannedOrPublishedDate() throws Exception {
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		when(contentService.period(10, 2026)).thenReturn(october);

		mvc.perform(get("/api/marketing/content").param("month", "10")
			.param("year", "2026")
			.param("dateField", "PLANNED")
			.param("contentType", "BLOG")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isOk());
		verify(contentService).search(argThat(f -> october.equals(f.period()) && f.dateField() == DateField.PLANNED
				&& f.types().equals(Set.of(ContentType.BLOG))), any(), eq(VIEWER));
		mvc.perform(get("/api/marketing/content").param("sort", "leads,desc")
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isBadRequest());
	}

	@Test
	void attachmentsFollowTheContentPermissions() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "outline.txt", "text/plain", "Outline".getBytes());
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SEO_ONLY)) {
			mvc.perform(get("/api/marketing/content/1/attachments").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
			mvc.perform(get("/api/marketing/content/1/attachments/9").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
		}
		mvc.perform(multipart("/api/marketing/content/1/attachments").file(file)
			.header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/marketing/content/1/attachments/9").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(contentService);

		mvc.perform(get("/api/marketing/content/1/attachments").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isOk());
		mvc.perform(multipart("/api/marketing/content/1/attachments").file(file)
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isOk());
		verify(contentService).addAttachment(eq(1L), any(), eq(EDITOR));
	}

	@Test
	void exportIsAnAttachment() throws Exception {
		when(contentService.export(any())).thenReturn("Title\r\n".getBytes());

		mvc.perform(get("/api/marketing/content/export").header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("content.csv")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

}
