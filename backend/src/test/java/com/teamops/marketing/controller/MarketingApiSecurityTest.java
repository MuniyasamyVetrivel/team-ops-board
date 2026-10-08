package com.teamops.marketing.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.csv.CsvImportService;
import com.teamops.common.csv.ImportDtos.ImportResult;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.marketing.dto.MarketingDtos.MarketingContext;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** The Digital Marketing route group is closed to everyone without MARKETING_VIEW. Services are mocked. */
@WebMvcTest(controllers = { MarketingController.class, MarketingImportController.class })
@SecuritySliceTest
class MarketingApiSecurityTest {

	/** A Digital Marketing employee as the dev seed sets one up. */
	private static final AuthenticatedUser MARKETER = new AuthenticatedUser(12L, "arun.kumar@teamops.local",
			"Arun Kumar", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private MarketingContextService contextService;

	@MockitoBean
	private CsvImportService importService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void employeesOutsideMarketingAreForbiddenEverywhere() throws Exception {
		for (String path : List.of("/api/marketing/context", "/api/marketing/integrations", "/api/marketing/imports",
				"/api/marketing/imports/keywords/template", "/api/marketing/anything-new")) {
			mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		mvc.perform(multipart("/api/marketing/imports/keywords/preview").file(csv())
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY))).andExpect(status().isForbidden());
		verifyNoInteractions(contextService, importService);
	}

	@Test
	void anonymousCallersMustSignIn() throws Exception {
		mvc.perform(get("/api/marketing/context")).andExpect(status().isUnauthorized());
	}

	@Test
	void marketingUsersAndSuperAdminsGetTheContext() throws Exception {
		when(contextService.context()).thenReturn(new MarketingContext(LocalDate.of(2026, 10, 8),
				new Period(10, 2026, "October 2026"), List.of(2027, 2026), List.of(), new BigDecimal("60")));

		mvc.perform(get("/api/marketing/context").header(HttpHeaders.AUTHORIZATION, bearer(MARKETER)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentPeriod.label").value("October 2026"))
			.andExpect(jsonPath("$.today").value("2026-10-08"));
		mvc.perform(get("/api/marketing/context").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isOk());
	}

	@Test
	void templatesDownloadAsCsvAttachments() throws Exception {
		when(importService.template(eq("keywords"), any())).thenReturn("keyword\r\n".getBytes());

		mvc.perform(get("/api/marketing/imports/keywords/template").header(HttpHeaders.AUTHORIZATION, bearer(MARKETER)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void commitPassesTheChecksumAndSkipFlag() throws Exception {
		when(importService.commit(eq("keywords"), any(), eq("abc"), eq(true), any(), any()))
			.thenReturn(new ImportResult("keywords", 4, 1));

		mvc.perform(multipart("/api/marketing/imports/keywords/commit").file(csv())
			.param("checksum", "abc")
			.param("skipInvalid", "true")
			.header(HttpHeaders.AUTHORIZATION, bearer(MARKETER)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(4))
			.andExpect(jsonPath("$.skipped").value(1));
		verify(importService).commit(eq("keywords"), any(), eq("abc"), eq(true), eq(MARKETER), any());

		mvc.perform(multipart("/api/marketing/imports/keywords/commit").file(csv())
			.header(HttpHeaders.AUTHORIZATION, bearer(MARKETER)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
	}

	private static MockMultipartFile csv() {
		return new MockMultipartFile("file", "keywords.csv", "text/csv", "keyword\nsap\n".getBytes());
	}

}
