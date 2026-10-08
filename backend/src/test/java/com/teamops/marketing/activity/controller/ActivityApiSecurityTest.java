package com.teamops.marketing.activity.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import com.teamops.marketing.activity.entity.OccurrenceStatus;
import com.teamops.marketing.activity.service.ActivityService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Activities: MARKETING_VIEW reads and acts on its own occurrences, MARKETING_EDIT configures. Service mocked. */
@WebMvcTest(controllers = ActivityController.class)
@SecuritySliceTest
class ActivityApiSecurityTest {

	private static final AuthenticatedUser MARKETER = new AuthenticatedUser(12L, "arun.kumar@teamops.local",
			"Arun Kumar", 7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "SEO_VIEW"));

	private static final AuthenticatedUser MANAGER = new AuthenticatedUser(5L, "priya.menon@teamops.local",
			"Priya Menon", 7L, Set.of("DEPARTMENT_MANAGER"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "MARKETING_EDIT"));

	private static final String VALID = """
			{"name":"Monthly SEO Ranking Update","departmentId":7,"frequency":"MONTHLY","startDate":"2026-10-01",
			 "dueOffsetDays":4,"taskTitleTemplate":"Update {month} keyword rankings","checklist":["Export positions"]}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private ActivityService activityService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void outsideMarketingEverythingIsForbidden() throws Exception {
		for (String path : List.of("/api/marketing/activities", "/api/marketing/activities/1",
				"/api/marketing/activities/1/occurrences", "/api/marketing/activity-occurrences")) {
			mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
				.andExpect(status().isForbidden());
		}
		mvc.perform(post("/api/marketing/activity-occurrences/1/complete").header(HttpHeaders.AUTHORIZATION,
				bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		verifyNoInteractions(activityService);
	}

	@Test
	void onlyManagersConfigureActivities() throws Exception {
		mvc.perform(post("/api/marketing/activities").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(MARKETER))).andExpect(status().isForbidden());
		verifyNoInteractions(activityService);

		mvc.perform(post("/api/marketing/activities").contentType(MediaType.APPLICATION_JSON)
			.content(VALID)
			.header(HttpHeaders.AUTHORIZATION, bearer(MANAGER))).andExpect(status().isCreated());
		verify(activityService).create(any(), eq(MANAGER), any());
	}

	@Test
	void activityInputIsValidated() throws Exception {
		mvc.perform(post("/api/marketing/activities").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\",\"departmentId\":7,\"dueOffsetDays\":400,\"checklist\":[\"\"]}")
			.header(HttpHeaders.AUTHORIZATION, bearer(MANAGER)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("name", "frequency", "startDate", "dueOffsetDays")));
		verifyNoInteractions(activityService);
	}

	@Test
	void marketersActOnOccurrencesAndFilterThem() throws Exception {
		mvc.perform(post("/api/marketing/activity-occurrences/9/complete").header(HttpHeaders.AUTHORIZATION,
				bearer(MARKETER))).andExpect(status().isOk());
		verify(activityService).complete(eq(9L), isNull(), eq(MARKETER), any());

		mvc.perform(post("/api/marketing/activity-occurrences/9/skip").contentType(MediaType.APPLICATION_JSON)
			.content("{\"notes\":\"Not needed this week\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(MARKETER))).andExpect(status().isOk());
		verify(activityService).skip(eq(9L), eq("Not needed this week"), eq(MARKETER), any());

		mvc.perform(get("/api/marketing/activity-occurrences").param("status", "PENDING", "IN_PROGRESS")
			.param("dueTo", "2026-10-31")
			.header(HttpHeaders.AUTHORIZATION, bearer(MARKETER))).andExpect(status().isOk());
		verify(activityService).occurrences(eq(Set.of(OccurrenceStatus.PENDING, OccurrenceStatus.IN_PROGRESS)), isNull(),
				eq(LocalDate.of(2026, 10, 31)), isNull(), isNull(), any(), eq(MARKETER));

		mvc.perform(get("/api/marketing/activity-occurrences").param("sort", "title,asc")
			.header(HttpHeaders.AUTHORIZATION, bearer(MARKETER))).andExpect(status().isBadRequest());
	}

}
