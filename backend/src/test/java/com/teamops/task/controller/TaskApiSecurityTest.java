package com.teamops.task.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.common.web.PageResponse;
import com.teamops.project.controller.ProjectController;
import com.teamops.project.service.ProjectService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;
import com.teamops.task.dto.DueFilter;
import com.teamops.task.dto.TaskSearchCriteria;
import com.teamops.task.dto.TaskView;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.service.TaskCollaborationService;
import com.teamops.task.service.TaskService;
import com.teamops.workload.WorkloadController;
import com.teamops.workload.WorkloadService;

/** Authorization, request binding and validation for the Phase 5 endpoints. Services are mocked; no database. */
@WebMvcTest(controllers = { TaskController.class, WorkloadController.class, ProjectController.class })
@SecuritySliceTest
class TaskApiSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private TaskService taskService;

	@MockitoBean
	private TaskCollaborationService collaborationService;

	@MockitoBean
	private WorkloadService workloadService;

	@MockitoBean
	private ProjectService projectService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void tasksRequireAuthenticationAndTaskView() throws Exception {
		mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(taskService);
	}

	@Test
	void searchBindsMultiValueFiltersAndViews() throws Exception {
		when(taskService.search(any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 25, 0, 0));

		mvc.perform(get("/api/tasks").param("status", "TODO", "BLOCKED")
			.param("due", "OVERDUE")
			.param("view", "ASSIGNED_TO_ME")
			.param("sort", "priority,desc")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isOk());

		ArgumentCaptor<TaskSearchCriteria> criteria = ArgumentCaptor.forClass(TaskSearchCriteria.class);
		verify(taskService).search(criteria.capture(), any(), eq(SliceAuth.EMPLOYEE));
		org.assertj.core.api.Assertions.assertThat(criteria.getValue().statuses())
			.isEqualTo(Set.of(TaskStatus.TODO, TaskStatus.BLOCKED));
		org.assertj.core.api.Assertions.assertThat(criteria.getValue().due()).isEqualTo(DueFilter.OVERDUE);
		org.assertj.core.api.Assertions.assertThat(criteria.getValue().view()).isEqualTo(TaskView.ASSIGNED_TO_ME);
	}

	@Test
	void invalidFiltersAndSortsAreRejected() throws Exception {
		String token = bearer(SliceAuth.EMPLOYEE);
		mvc.perform(get("/api/tasks").param("due", "YESTERDAY").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		mvc.perform(get("/api/tasks").param("sort", "description,asc").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SORT"));
	}

	@Test
	void createAndUpdateValidateTheBody() throws Exception {
		String token = bearer(SliceAuth.EMPLOYEE);
		mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"  \",\"estimatedHours\":-1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'estimatedHours')]").exists());
		mvc.perform(put("/api/tasks/5").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Ok\",\"departmentId\":5,\"priority\":\"HIGH\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'version')]").exists());
		verifyNoInteractions(taskService);
	}

	@Test
	void creatingNeedsTaskCreate() throws Exception {
		AuthenticatedUser viewer = new AuthenticatedUser(70L, "viewer@teamops.local", "Viewer", 5L, Set.of("EMPLOYEE"),
				Set.of("TASK_VIEW"));
		mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(viewer))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Hello\"}")).andExpect(status().isForbidden());
	}

	@Test
	void uploadWithoutAFileIsABadRequest() throws Exception {
		mvc.perform(multipart("/api/tasks/5/attachments").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void downloadsAreForcedAttachmentsWithNosniff() throws Exception {
		when(collaborationService.download(eq(5L), eq(9L), any())).thenReturn(new TaskCollaborationService.Download(
				"report final.pdf", "application/pdf", 3, new ByteArrayResource(new byte[] { 1, 2, 3 })));

		mvc.perform(get("/api/tasks/5/attachments/9").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));
	}

	@Test
	void workloadBindsFiltersAndRejectsUnknownSorts() throws Exception {
		String token = bearer(SliceAuth.SUPER_ADMIN);
		mvc.perform(get("/api/workload").param("sort", "LOUDEST").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/workload").param("from", "07-10-2026").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/workload").param("from", "2026-10-01")
			.param("to", "2026-10-31")
			.param("sort", "MOST_OVERDUE")
			.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
	}

	@Test
	void projectOptionsNeedProjectViewOrTaskCreate() throws Exception {
		mvc.perform(get("/api/projects/options").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/projects/options").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk());
	}

}
