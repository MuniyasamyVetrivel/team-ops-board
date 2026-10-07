package com.teamops.dashboard;

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

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.calendar.controller.CalendarController;
import com.teamops.calendar.dto.CalendarDtos.CalendarResponse;
import com.teamops.calendar.service.CalendarService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.common.web.PageResponse;
import com.teamops.notification.controller.NotificationController;
import com.teamops.notification.dto.UnreadCount;
import com.teamops.notification.service.NotificationService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Authorization and request binding for the Phase 6 endpoints. Services are mocked; no database. */
@WebMvcTest(controllers = { DashboardController.class, NotificationController.class, CalendarController.class })
@SecuritySliceTest
class DashboardApiSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private DashboardService dashboardService;

	@MockitoBean
	private NotificationService notificationService;

	@MockitoBean
	private CalendarService calendarService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void dashboardNeedsAuthenticationAndDashboardView() throws Exception {
		mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(dashboardService);

		mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk());
		verify(dashboardService).dashboard(SliceAuth.EMPLOYEE);
	}

	@Test
	void notificationsAreForAnySignedInUserAndAlwaysTheirOwn() throws Exception {
		mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
		when(notificationService.unreadCount(SliceAuth.NOBODY)).thenReturn(new UnreadCount(3));
		when(notificationService.list(eq(SliceAuth.NOBODY), eq(true), any()))
			.thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		mvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unread").value(3));
		mvc.perform(get("/api/notifications").param("unread", "true")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY))).andExpect(status().isOk());
		mvc.perform(post("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isOk());
		verify(notificationService).markAllRead(SliceAuth.NOBODY);
	}

	@Test
	void notificationSortIsWhitelisted() throws Exception {
		when(notificationService.list(any(), eq(false), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
		mvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk());
	}

	@Test
	void calendarNeedsCalendarViewAndBindsTheRange() throws Exception {
		mvc.perform(get("/api/calendar").param("from", "2026-10-01")
			.param("to", "2026-10-31")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY))).andExpect(status().isForbidden());

		LocalDate from = LocalDate.of(2026, 10, 1);
		LocalDate to = LocalDate.of(2026, 10, 31);
		when(calendarService.range(from, to, true, SliceAuth.EMPLOYEE))
			.thenReturn(new CalendarResponse(from, from, to, List.of()));
		mvc.perform(get("/api/calendar").param("from", "2026-10-01")
			.param("to", "2026-10-31")
			.param("mine", "true")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray());

		mvc.perform(get("/api/calendar").param("from", "not-a-date")
			.param("to", "2026-10-31")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isBadRequest());
	}

	@Test
	void eventChangesNeedCalendarEditAndAValidBody() throws Exception {
		String body = """
				{"title":"Offsite","eventType":"TEAM_EVENT","allDay":true,"startDate":"2026-10-15","endDate":"2026-10-15"}
				""";
		mvc.perform(post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/calendar/events/5").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(calendarService);

		mvc.perform(post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\" \",\"allDay\":true}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))).andExpect(status().isCreated());
	}

}
