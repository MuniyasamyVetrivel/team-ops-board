package com.teamops.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.teamops.admin.service.AuditLogService;
import com.teamops.admin.service.SettingsAdminService;
import com.teamops.approval.controller.ApprovalController;
import com.teamops.approval.service.ApprovalService;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditCatalog;
import com.teamops.common.report.ReportExporters;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.common.web.PageResponse;
import com.teamops.search.controller.GlobalSearchController;
import com.teamops.search.dto.SearchDtos.SearchResults;
import com.teamops.search.service.GlobalSearchService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;
import com.teamops.user.controller.AccessCatalogController;
import com.teamops.user.service.AccessCatalogService;
import com.teamops.user.service.RolePermissionService;

/** Phase 21: who may change settings, the permission matrix and approval types, read the audit log, and search. */
@WebMvcTest(controllers = { SettingsController.class, AuditLogController.class, GlobalSearchController.class,
		AccessCatalogController.class, ApprovalController.class })
@SecuritySliceTest
class AdminPhase21ApiSecurityTest {

	/** Holds PERMISSION_MANAGE without being a Super Admin. */
	private static final AuthenticatedUser ACCESS_ADMIN = new AuthenticatedUser(12L, "access@teamops.local",
			"Access Admin", 3L, Set.of("DEPARTMENT_MANAGER"),
			SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "PERMISSION_MANAGE", "USER_MANAGE"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private SettingsAdminService settingsAdminService;

	@MockitoBean
	private AuditLogService auditLogService;

	@MockitoBean
	private ReportExporters reportExporters;

	@MockitoBean
	private GlobalSearchService globalSearchService;

	@MockitoBean
	private AccessCatalogService accessCatalogService;

	@MockitoBean
	private RolePermissionService rolePermissionService;

	@MockitoBean
	private ApprovalService approvalService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void settingsNeedSettingsManage() throws Exception {
		when(settingsAdminService.list()).thenReturn(List.of());
		mvc.perform(get("/api/admin/settings")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/admin/settings").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isForbidden());
		mvc.perform(put("/api/admin/settings/workload.windowDays").header(HttpHeaders.AUTHORIZATION,
				bearer(SliceAuth.EMPLOYEE))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"value\":\"21\"}")).andExpect(status().isForbidden());
		verifyNoInteractions(settingsAdminService);

		mvc.perform(get("/api/admin/settings").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isOk());
	}

	@Test
	void aSettingUpdateNeedsVersionAndValue() throws Exception {
		mvc.perform(put("/api/admin/settings/workload.windowDays").header(HttpHeaders.AUTHORIZATION,
				bearer(SliceAuth.SUPER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"value\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'version')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'value')]").exists());
	}

	@Test
	void theAuditLogNeedsAuditView() throws Exception {
		when(auditLogService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));
		mvc.perform(get("/api/admin/audit-logs").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/audit-logs/catalog").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/audit-logs/export").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(auditLogService);

		mvc.perform(get("/api/admin/audit-logs").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.param("action", "TASK_CREATED", "LOGIN")
			.param("module", "TASKS")
			.param("event", "TASK_CREATION")
			.param("from", "2026-10-01")
			.param("to", "2026-10-09")
			.param("sort", "action,asc")).andExpect(status().isOk());
		verify(auditLogService).search(argThat(f -> f.actions().equals(Set.of(AuditAction.TASK_CREATED, AuditAction.LOGIN))
				&& f.module() == AuditCatalog.Module.TASKS && f.event() == AuditCatalog.BriefEvent.TASK_CREATION
				&& f.from().toString().equals("2026-10-01")), any());
	}

	@Test
	void auditFiltersAreValidated() throws Exception {
		String token = bearer(SliceAuth.SUPER_ADMIN);
		mvc.perform(get("/api/admin/audit-logs").header(HttpHeaders.AUTHORIZATION, token).param("action", "DROP_TABLES"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		mvc.perform(get("/api/admin/audit-logs").header(HttpHeaders.AUTHORIZATION, token).param("sort", "details,asc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SORT"));
		mvc.perform(get("/api/admin/audit-logs").header(HttpHeaders.AUTHORIZATION, token).param("from", "yesterday"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void onlyASuperAdminEditsARolesPermissions() throws Exception {
		String body = "{\"version\":0,\"permissions\":[\"TASK_VIEW\"]}";
		mvc.perform(put("/api/roles/EMPLOYEE/permissions").header(HttpHeaders.AUTHORIZATION, bearer(ACCESS_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isForbidden());
		mvc.perform(put("/api/roles/EMPLOYEE/permissions").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isForbidden());
		verifyNoInteractions(rolePermissionService);
		// Reading the matrix still works for access administrators.
		mvc.perform(get("/api/roles").header(HttpHeaders.AUTHORIZATION, bearer(ACCESS_ADMIN))).andExpect(status().isOk());

		mvc.perform(put("/api/roles/EMPLOYEE/permissions").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isOk());
		verify(rolePermissionService).update(eq("EMPLOYEE"), argThat(r -> r.permissions().equals(Set.of("TASK_VIEW"))),
				eq(SliceAuth.SUPER_ADMIN), any());
		mvc.perform(put("/api/roles/EMPLOYEE/permissions").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"permissions\":[\"TASK_VIEW\"]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
	}

	@Test
	void approvalTypesNeedApprovalConfigure() throws Exception {
		String create = """
				{"code":"TRAVEL","name":"Travel request","requiresAmount":true,"steps":[{"approverKind":"DEPARTMENT_MANAGER"}]}
				""";
		mvc.perform(post("/api/approvals/types").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))
			.contentType(MediaType.APPLICATION_JSON)
			.content(create)).andExpect(status().isForbidden());
		mvc.perform(put("/api/approvals/types/3").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"name\":\"Travel\",\"requiresAmount\":false,\"active\":false}"))
			.andExpect(status().isForbidden());
		verifyNoInteractions(approvalService);

		String token = bearer(SliceAuth.SUPER_ADMIN);
		mvc.perform(post("/api/approvals/types").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(create)).andExpect(status().isCreated());
		mvc.perform(post("/api/approvals/types").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"travel request\",\"name\":\"\",\"steps\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.message =~ /Use 2.40 capital letters.*/)]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.message == 'Name is required')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.message == 'At least one step is required')]").exists());
		verify(approvalService, org.mockito.Mockito.times(1)).createType(any(), any(), any());
	}

	@Test
	void everySignedInUserMaySearch() throws Exception {
		when(globalSearchService.search(any(), any(), any(Integer.class), any())).thenReturn(new SearchResults("ab", List.of()));
		mvc.perform(get("/api/search").param("q", "ab")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/search").param("q", "ab").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isOk());
		verify(globalSearchService).search(eq("ab"), any(), eq(5), eq(SliceAuth.NOBODY));
		mvc.perform(get("/api/search").param("q", "ab")
			.param("group", "SPACESHIPS")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY))).andExpect(status().isBadRequest());
	}

}
