package com.teamops.celebration.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.teamops.celebration.service.EmployeeService;
import com.teamops.celebration.service.ImportantDayService;
import com.teamops.celebration.service.LinkedInPostService;
import com.teamops.celebration.service.MarketingEventService;
import com.teamops.celebration.service.OccasionCalendar;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/**
 * Brief: every signed-in user sees celebrations, important days and events; only Super Admins and CELEBRATIONS_EDIT
 * holders change them. Services mocked.
 */
@WebMvcTest(controllers = { EmployeeController.class, ImportantDayController.class, MarketingEventController.class,
		CelebrationController.class })
@SecuritySliceTest
class CelebrationApiSecurityTest {

	private static final AuthenticatedUser EDITOR = new AuthenticatedUser(5L, "priya.menon@teamops.local",
			"Priya Menon", 7L, Set.of("EMPLOYEE"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW", "CELEBRATIONS_EDIT"));

	/** Every other Digital Marketing permission, but not this one. */
	private static final AuthenticatedUser MARKETER = new AuthenticatedUser(12L, "arun.kumar@teamops.local",
			"Arun Kumar", 7L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW",
					"MARKETING_EDIT", "CAMPAIGN_VIEW", "CAMPAIGN_EDIT", "CONTENT_VIEW", "CONTENT_EDIT"));

	private static final String EMPLOYEE_JSON = """
			{"employeeCode":"TRI-0101","firstName":"Priya","lastName":"Menon","dateOfBirth":"1992-10-14",
			 "designation":"Digital Marketing Manager","reportingManager":"Rakesh"}
			""";

	private static final String DAY_JSON = """
			{"name":"Diwali","date":"2026-11-08","category":"FESTIVAL","region":"INDIA","recurrence":"ONE_OFF"}
			""";

	private static final String EVENT_JSON = """
			{"title":"SAP testing webinar","eventType":"WEBINAR","startAt":"2026-10-18T09:30:00Z","ownerId":5}
			""";

	private static final String POST_JSON = """
			{"subjectType":"IMPORTANT_DAY","subjectId":4,"occurrenceDate":"2026-11-08","postNeeded":true,
			 "status":"DRAFTED"}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private EmployeeService employeeService;

	@MockitoBean
	private ImportantDayService importantDayService;

	@MockitoBean
	private MarketingEventService eventService;

	@MockitoBean
	private OccasionCalendar occasionCalendar;

	@MockitoBean
	private LinkedInPostService linkedInPostService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void everySignedInUserCanLook() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.NOBODY, SliceAuth.EMPLOYEE)) {
			for (String path : List.of("/api/celebrations/employees", "/api/celebrations/employees/1",
					"/api/celebrations/important-days", "/api/celebrations/important-days/1",
					"/api/celebrations/important-days/export", "/api/celebrations/important-days/1/attachments",
					"/api/celebrations/events", "/api/celebrations/events/1", "/api/celebrations/events/1/attachments",
					"/api/celebrations/upcoming", "/api/celebrations/linkedin-posts")) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isOk());
			}
		}
		mvc.perform(get("/api/celebrations/upcoming")).andExpect(status().isUnauthorized());
	}

	@Test
	void onlyCelebrationEditorsChangeAnything() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, MARKETER)) {
			for (AbstractMockHttpServletRequestBuilder<?> request : changes()) {
				mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isForbidden());
			}
			// The export holds full birth dates.
			mvc.perform(get("/api/celebrations/employees/export").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
		}
		verifyNoInteractions(employeeService, importantDayService, eventService, linkedInPostService);

		mvc.perform(post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON)
			.content(EMPLOYEE_JSON)
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isCreated());
		verify(employeeService).create(any(), eq(EDITOR), any());
		mvc.perform(put("/api/celebrations/linkedin-posts").contentType(MediaType.APPLICATION_JSON)
			.content(POST_JSON)
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))).andExpect(status().isOk());
		verify(linkedInPostService).save(any(), eq(EDITOR), any());
		mvc.perform(post("/api/celebrations/important-days").contentType(MediaType.APPLICATION_JSON)
			.content(DAY_JSON)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))).andExpect(status().isCreated());
		verify(importantDayService).create(any(), eq(SliceAuth.SUPER_ADMIN), any());
	}

	@Test
	void exportsAreDownloads() throws Exception {
		when(employeeService.export(any())).thenReturn("employee_id\n".getBytes());
		mvc.perform(get("/api/celebrations/employees/export").header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"employees.csv\""))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void inputIsValidated() throws Exception {
		mvc.perform(post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON)
			.content("{\"workEmail\":\"not-an-email\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("employeeCode", "dateOfBirth", "workEmail")));
		mvc.perform(post("/api/celebrations/important-days").contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("name", "date", "category", "region", "recurrence")));
		mvc.perform(post("/api/celebrations/events").contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItems("title", "eventType", "startAt", "ownerId")));
		mvc.perform(put("/api/celebrations/linkedin-posts").contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field",
					hasItems("subjectType", "subjectId", "occurrenceDate", "postNeeded", "status")));
	}

	private static List<AbstractMockHttpServletRequestBuilder<?>> changes() {
		MockMultipartFile file = new MockMultipartFile("file", "creative.png", "image/png", new byte[] { 1 });
		return List.<AbstractMockHttpServletRequestBuilder<?>>of(
				post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON).content(EMPLOYEE_JSON),
				put("/api/celebrations/employees/1").contentType(MediaType.APPLICATION_JSON).content(EMPLOYEE_JSON),
				delete("/api/celebrations/employees/1"),
				multipart(HttpMethod.PUT, "/api/celebrations/employees/1/photo").file(file),
				delete("/api/celebrations/employees/1/photo"),
				post("/api/celebrations/important-days").contentType(MediaType.APPLICATION_JSON).content(DAY_JSON),
				put("/api/celebrations/important-days/1").contentType(MediaType.APPLICATION_JSON).content(DAY_JSON),
				delete("/api/celebrations/important-days/1"),
				multipart("/api/celebrations/important-days/1/attachments").file(file),
				delete("/api/celebrations/important-days/1/attachments/3"),
				post("/api/celebrations/events").contentType(MediaType.APPLICATION_JSON).content(EVENT_JSON),
				put("/api/celebrations/events/1").contentType(MediaType.APPLICATION_JSON).content(EVENT_JSON),
				delete("/api/celebrations/events/1"),
				multipart("/api/celebrations/events/1/attachments").file(file),
				delete("/api/celebrations/events/1/attachments/3"),
				put("/api/celebrations/linkedin-posts").contentType(MediaType.APPLICATION_JSON).content(POST_JSON));
	}

}
