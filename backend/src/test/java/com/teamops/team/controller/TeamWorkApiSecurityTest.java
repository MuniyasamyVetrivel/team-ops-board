package com.teamops.team.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;
import com.teamops.team.dto.WorkHistoryDtos.DateField;
import com.teamops.team.service.WorkHistoryService;
import com.teamops.team.service.WorkHistoryService.Export;
import com.teamops.team.service.WorkHistoryService.Filter;

/** Work history needs TEAM_VIEW plus TASK_VIEW (tasks) or TICKET_VIEW (tickets); the service checks whose. */
@WebMvcTest(controllers = TeamWorkController.class)
@SecuritySliceTest
class TeamWorkApiSecurityTest {

	/** Sees the directory, but not tasks or tickets. */
	private static final AuthenticatedUser DIRECTORY_ONLY = new AuthenticatedUser(8L, "dir@teamops.local", "Directory",
			5L, Set.of("EMPLOYEE"), Set.of("TEAM_VIEW"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private WorkHistoryService workHistoryService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void needsTheModulePermissions() throws Exception {
		for (String path : List.of("/api/team/4/tasks", "/api/team/4/tasks/summary", "/api/team/4/tasks/export",
				"/api/team/4/tickets", "/api/team/4/tickets/summary", "/api/team/4/tickets/export")) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized());
			for (AuthenticatedUser user : List.of(SliceAuth.NOBODY, DIRECTORY_ONLY)) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(workHistoryService);
	}

	@Test
	void passesTheFiltersToTheService() throws Exception {
		mvc.perform(get("/api/team/4/tasks").param("status", "COMPLETED", "OVERDUE")
			.param("dateField", "COMPLETED")
			.param("from", "2026-09-10")
			.param("to", "2026-09-20")
			.param("priority", "HIGH")
			.param("projectId", "3")
			.param("search", "plan")
			.param("sort", "priority,desc")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isOk());
		verify(workHistoryService).tasks(eq(4L),
				eq(new Filter(Set.of("COMPLETED", "OVERDUE"), DateField.COMPLETED, LocalDate.of(2026, 9, 10),
						LocalDate.of(2026, 9, 20), 3L, Set.of("HIGH"), "plan")),
				any(), eq(SliceAuth.EMPLOYEE));

		mvc.perform(get("/api/team/4/tasks").param("sort", "title,asc").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void exportsAreCsvDownloads() throws Exception {
		when(workHistoryService.exportTasks(eq(4L), any(), any(Sort.class), any()))
			.thenReturn(new Export("karthik-raj-tasks.csv", "Task ID\n".getBytes()));
		mvc.perform(get("/api/team/4/tasks/export").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"karthik-raj-tasks.csv\""))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

}
