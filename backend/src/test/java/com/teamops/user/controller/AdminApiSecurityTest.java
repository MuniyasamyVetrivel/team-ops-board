package com.teamops.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.teamops.common.web.PageResponse;
import com.teamops.department.controller.DepartmentController;
import com.teamops.department.service.DepartmentService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;
import com.teamops.team.controller.TeamController;
import com.teamops.team.service.TeamService;
import com.teamops.user.service.AccessCatalogService;
import com.teamops.user.service.RolePermissionService;
import com.teamops.user.service.UserService;

/** Authorization matrix and request validation for the Phase 4 endpoints. Services are mocked; no database. */
@WebMvcTest(controllers = { UserController.class, AccessCatalogController.class, DepartmentController.class,
		TeamController.class })
@SecuritySliceTest
class AdminApiSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private AccessCatalogService accessCatalogService;

	@MockitoBean
	private RolePermissionService rolePermissionService;

	@MockitoBean
	private DepartmentService departmentService;

	@MockitoBean
	private TeamService teamService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void userAdminEndpointsRequireAuthentication() throws Exception {
		mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/team")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/departments")).andExpect(status().isUnauthorized());
	}

	@Test
	void employeeCannotListOrCreateUsers() throws Exception {
		String token = bearer(SliceAuth.EMPLOYEE);

		mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(validCreateBody()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/roles").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
		verifyNoInteractions(userService, accessCatalogService);
	}

	@Test
	void superAdminCanListUsers() throws Exception {
		when(userService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		mvc.perform(get("/api/users?search=karthik&status=ACTIVE&sort=lastLogin,desc")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void unknownSortFieldIsRejected() throws Exception {
		mvc.perform(get("/api/users?sort=passwordHash,asc").header(HttpHeaders.AUTHORIZATION,
				bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SORT"));
	}

	@Test
	void invalidStatusFilterIsRejected() throws Exception {
		mvc.perform(get("/api/users?status=SLEEPING").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
		mvc.perform(get("/api/users/abc").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createUserValidatesTheBody() throws Exception {
		mvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"not-an-email\",\"roles\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'firstName')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'departmentId')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'roles')]").exists());
		verifyNoInteractions(userService);
	}

	@Test
	void validCreateReturns201() throws Exception {
		mvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content(validCreateBody()))
			.andExpect(status().isCreated());
	}

	@Test
	void changingAccessNeedsPermissionManageNotJustUserManage() throws Exception {
		mvc.perform(put("/api/users/7/access").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.USER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"roles\":[\"EMPLOYEE\"],\"permissions\":[]}"))
			.andExpect(status().isForbidden());
		// ...but USER_MANAGE is enough to disable someone.
		mvc.perform(post("/api/users/7/disable").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.USER_ADMIN)))
			.andExpect(status().isOk());
	}

	@Test
	void everySignedInUserCanListDepartmentsButNotCreateThem() throws Exception {
		String token = bearer(SliceAuth.NOBODY);
		when(departmentService.list()).thenReturn(List.of());

		mvc.perform(get("/api/departments").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
		mvc.perform(post("/api/departments").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"QA\",\"code\":\"QA\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void departmentCodeFormatIsValidated() throws Exception {
		mvc.perform(post("/api/departments").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Quality\",\"code\":\"has spaces!\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
	}

	@Test
	void memberEndpointsDelegateScopeChecksToTheService() throws Exception {
		mvc.perform(put("/api/departments/7/members/40").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"MEMBER\"}"))
			.andExpect(status().isOk());
		org.mockito.Mockito.verify(departmentService)
			.upsertMember(eq(7L), eq(40L), any(), eq(SliceAuth.EMPLOYEE), any());
	}

	@Test
	void teamDirectoryNeedsTeamView() throws Exception {
		when(teamService.directory(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 24, 0, 0));

		mvc.perform(get("/api/team").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk());
		mvc.perform(get("/api/team").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isForbidden());
	}

	private static String validCreateBody() {
		return """
				{"email":"new.person@teamops.local","password":"Welcome123","firstName":"New",
				 "departmentId":5,"roles":["EMPLOYEE"],"permissions":[]}
				""";
	}

}
