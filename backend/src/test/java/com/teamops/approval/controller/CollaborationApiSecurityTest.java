package com.teamops.approval.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.announcement.controller.AnnouncementController;
import com.teamops.announcement.entity.AnnouncementState;
import com.teamops.announcement.service.AnnouncementService;
import com.teamops.approval.dto.ApprovalDtos;
import com.teamops.approval.service.ApprovalService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.document.controller.DocumentController;
import com.teamops.document.service.DocumentService;
import com.teamops.knowledge.controller.KnowledgeController;
import com.teamops.knowledge.service.KnowledgeService;
import com.teamops.project.controller.ProjectController;
import com.teamops.project.service.ProjectService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Authorization, request binding and validation for the Phase 8 endpoints. Services are mocked; no database. */
@WebMvcTest(controllers = { ApprovalController.class, ProjectController.class, AnnouncementController.class,
		KnowledgeController.class, DocumentController.class })
@SecuritySliceTest
class CollaborationApiSecurityTest {

	/** Department manager permissions from V2. */
	private static final AuthenticatedUser MANAGER = new AuthenticatedUser(3L, "sanjay.varma@teamops.local",
			"Sanjay Varma", 5L, Set.of("DEPARTMENT_MANAGER"),
			Set.of("DASHBOARD_VIEW", "PROJECT_VIEW", "PROJECT_EDIT", "APPROVAL_VIEW", "APPROVAL_DECIDE",
					"ANNOUNCEMENT_MANAGE", "KB_VIEW", "KB_EDIT", "DOCUMENT_VIEW", "DOCUMENT_EDIT"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private ApprovalService approvalService;

	@MockitoBean
	private ProjectService projectService;

	@MockitoBean
	private AnnouncementService announcementService;

	@MockitoBean
	private KnowledgeService knowledgeService;

	@MockitoBean
	private DocumentService documentService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void everythingNeedsAuthenticationAndTheModulesViewPermission() throws Exception {
		for (String path : new String[] { "/api/approvals", "/api/projects", "/api/announcements",
				"/api/knowledge-base/articles", "/api/documents" }) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized());
			mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
				.andExpect(status().isForbidden());
		}
		verifyNoInteractions(approvalService, projectService, announcementService, knowledgeService, documentService);
	}

	@Test
	void employeesRaiseApprovalsButCannotDecideOrConfigure() throws Exception {
		String request = "{\"typeId\":3,\"title\":\"New monitor\",\"amount\":15000.50,\"currency\":\"INR\"}";
		mvc.perform(post("/api/approvals").contentType(MediaType.APPLICATION_JSON)
			.content(request)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isCreated());
		verify(approvalService).submit(eq(new ApprovalDtos.Submit(3L, "New monitor", null, new BigDecimal("15000.50"),
				"INR", null)), eq(SliceAuth.EMPLOYEE), any());

		mvc.perform(post("/api/approvals/9/decision").contentType(MediaType.APPLICATION_JSON)
			.content("{\"decision\":\"APPROVE\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		mvc.perform(put("/api/approvals/types/3/steps").contentType(MediaType.APPLICATION_JSON)
			.content("{\"steps\":[{\"approverKind\":\"DEPARTMENT_MANAGER\"}]}")
			.header(HttpHeaders.AUTHORIZATION, bearer(MANAGER))).andExpect(status().isForbidden());
	}

	@Test
	void approvalRequestsAreValidated() throws Exception {
		mvc.perform(post("/api/approvals").contentType(MediaType.APPLICATION_JSON)
			.content("{\"typeId\":3,\"title\":\"x\",\"amount\":-1,\"currency\":\"rupees\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mvc.perform(put("/api/approvals/types/3/steps").contentType(MediaType.APPLICATION_JSON)
			.content("{\"steps\":[]}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))).andExpect(status().isBadRequest());
		mvc.perform(get("/api/approvals").param("view", "TO_DECIDE")
			.header(HttpHeaders.AUTHORIZATION, bearer(MANAGER))).andExpect(status().isOk());
		verify(approvalService).search(eq(new ApprovalDtos.Search(null, null, null, ApprovalDtos.View.TO_DECIDE)),
				any(), eq(MANAGER));
	}

	@Test
	void projectsAreReadableByEmployeesButChangedOnlyWithProjectEdit() throws Exception {
		mvc.perform(get("/api/projects/4").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk());
		mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Website revamp\",\"departmentId\":5}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		mvc.perform(post("/api/projects/4/milestones").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Launch\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		mvc.perform(put("/api/projects/4").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"name\":\"x\",\"departmentId\":5,\"status\":\"ACTIVE\",\"progressOverride\":120}")
			.header(HttpHeaders.AUTHORIZATION, bearer(MANAGER))).andExpect(status().isBadRequest());
	}

	@Test
	void announcementsAreReadByEveryoneAndPublishedByManagers() throws Exception {
		mvc.perform(get("/api/announcements").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk());
		verify(announcementService).list(eq(AnnouncementState.ACTIVE), any(), eq(SliceAuth.EMPLOYEE));
		mvc.perform(post("/api/announcements/5/acknowledge").header(HttpHeaders.AUTHORIZATION,
				bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isNoContent());

		String body = "{\"title\":\"Office closed\",\"body\":\"Friday\",\"priority\":\"NORMAL\"}";
		mvc.perform(post("/api/announcements").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		mvc.perform(post("/api/announcements").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header(HttpHeaders.AUTHORIZATION, bearer(MANAGER))).andExpect(status().isCreated());
	}

	@Test
	void knowledgeBaseEditingNeedsKbEdit() throws Exception {
		mvc.perform(get("/api/knowledge-base/articles").param("search", "vpn")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isOk());
		String article = "{\"title\":\"Reset VPN\",\"body\":\"Steps\",\"categoryId\":3,\"status\":\"PUBLISHED\"}";
		mvc.perform(post("/api/knowledge-base/articles").contentType(MediaType.APPLICATION_JSON)
			.content(article)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		mvc.perform(post("/api/knowledge-base/articles").contentType(MediaType.APPLICATION_JSON)
			.content(article)
			.header(HttpHeaders.AUTHORIZATION, bearer(MANAGER))).andExpect(status().isCreated());
		verify(knowledgeService).save(isNull(), any(), eq(MANAGER), any());
	}

	@Test
	void documentUploadsNeedDocumentEditAndBindTheirFields() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "policy.pdf", "application/pdf", new byte[] { 1, 2 });
		mvc.perform(multipart("/api/documents").file(file)
			.param("name", "Leave policy")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());

		mvc.perform(multipart("/api/documents").file(file)
			.param("name", "Leave policy")
			.param("departmentId", "5")
			.header(HttpHeaders.AUTHORIZATION, bearer(MANAGER))).andExpect(status().isCreated());
		verify(documentService).upload(any(), eq("Leave policy"), isNull(), eq(5L), isNull(), eq(MANAGER), any());
	}

}
