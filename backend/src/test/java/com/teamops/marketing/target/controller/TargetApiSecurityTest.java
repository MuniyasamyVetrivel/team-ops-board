package com.teamops.marketing.target.controller;

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
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.target.dto.TargetDtos.TrendView;
import com.teamops.marketing.target.service.TargetService;
import com.teamops.marketing.target.service.TargetTypeService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Targets: TARGET_VIEW reads, TARGET_EDIT changes targets, MARKETING_EDIT manages types. Services are mocked. */
@WebMvcTest(controllers = { TargetController.class, TargetTypeController.class })
@SecuritySliceTest
class TargetApiSecurityTest {

	/** The SEO executive in the dev seed: can view targets but not change them. */
	private static final AuthenticatedUser TARGET_READER = new AuthenticatedUser(12L, "arun.kumar@teamops.local",
			"Arun Kumar", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW", "TARGET_VIEW"));

	/** Sets targets but cannot manage target types. */
	private static final AuthenticatedUser TARGET_EDITOR = new AuthenticatedUser(14L, "targets@teamops.local",
			"Target Editor", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "TARGET_VIEW", "TARGET_EDIT"));

	/** Marketing access without targets. */
	private static final AuthenticatedUser SEO_ONLY = new AuthenticatedUser(13L, "seo@teamops.local", "Seo Only", 7L,
			Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW"));

	private static final String VALID_TARGET = "{\"typeId\":1,\"month\":10,\"year\":2026,\"targetValue\":250,\"departmentId\":7}";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private TargetService targetService;

	@MockitoBean
	private TargetTypeService typeService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void targetsNeedTargetView() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SEO_ONLY)) {
			for (String path : List.of("/api/marketing/targets", "/api/marketing/targets/1",
					"/api/marketing/targets/trend?typeId=1", "/api/marketing/target-types")) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(targetService, typeService);
	}

	@Test
	void readersCannotChangeTargetsAndEditorsCannotManageTypes() throws Exception {
		mvc.perform(post("/api/marketing/targets").contentType(MediaType.APPLICATION_JSON)
			.content(VALID_TARGET)
			.header(HttpHeaders.AUTHORIZATION, bearer(TARGET_READER))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/marketing/targets/1").header(HttpHeaders.AUTHORIZATION, bearer(TARGET_READER)))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/marketing/target-types").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Webinars\",\"unit\":\"COUNT\",\"actualSource\":\"MANUAL\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(TARGET_EDITOR))).andExpect(status().isForbidden());
		verifyNoInteractions(targetService, typeService);

		mvc.perform(post("/api/marketing/targets").contentType(MediaType.APPLICATION_JSON)
			.content(VALID_TARGET)
			.header(HttpHeaders.AUTHORIZATION, bearer(TARGET_EDITOR))).andExpect(status().isCreated());
		verify(targetService).create(any(), eq(TARGET_EDITOR), any());
		mvc.perform(post("/api/marketing/target-types").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Webinars\",\"unit\":\"COUNT\",\"actualSource\":\"MANUAL\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))).andExpect(status().isCreated());
	}

	@Test
	void targetInputIsValidated() throws Exception {
		mvc.perform(post("/api/marketing/targets").contentType(MediaType.APPLICATION_JSON)
			.content("{\"typeId\":1,\"month\":13,\"year\":2026,\"targetValue\":0,\"actualValue\":-1,\"departmentId\":7}")
			.header(HttpHeaders.AUTHORIZATION, bearer(TARGET_EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("month", "targetValue", "actualValue")));
		mvc.perform(post("/api/marketing/targets/monthly").contentType(MediaType.APPLICATION_JSON)
			.content("{\"month\":10,\"year\":2026,\"departmentId\":7,\"entries\":[]}")
			.header(HttpHeaders.AUTHORIZATION, bearer(TARGET_EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("entries")));
		mvc.perform(post("/api/marketing/target-types").contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"9 bad\",\"name\":\"\",\"behindThresholdPct\":120}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field",
					hasItems("code", "name", "unit", "actualSource", "behindThresholdPct")));
		verifyNoInteractions(targetService, typeService);
	}

	@Test
	void trendTakesTheViewAndYear() throws Exception {
		when(targetService.period(null, 2025)).thenReturn(new MarketingPeriod(10, 2025));

		mvc.perform(get("/api/marketing/targets/trend").param("typeId", "3")
			.param("view", "QUARTER")
			.param("year", "2025")
			.header(HttpHeaders.AUTHORIZATION, bearer(TARGET_READER))).andExpect(status().isOk());
		verify(targetService).trend(3L, TrendView.QUARTER, 2025);

		mvc.perform(get("/api/marketing/targets/trend").param("typeId", "3")
			.param("view", "WEEK")
			.header(HttpHeaders.AUTHORIZATION, bearer(TARGET_READER))).andExpect(status().isBadRequest());
	}

}
