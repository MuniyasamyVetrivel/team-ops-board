package com.teamops.recyclebin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import com.teamops.common.web.PageResponse;
import com.teamops.recyclebin.dto.RecycleBinDtos.Restored;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/**
 * Recycle bin API: RECYCLE_BIN_MANAGE only (Super Admin by default; 403 for everyone else) for viewing, restoring and
 * permanent delete, which must name the item.
 */
@WebMvcTest(controllers = RecycleBinController.class)
@SecuritySliceTest
class RecycleBinApiSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private RecycleBinService recycleBin;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void listingPassesFiltersAndTheViewer() throws Exception {
		mvc.perform(get("/api/recycle-bin")).andExpect(status().isUnauthorized());
		verifyNoInteractions(recycleBin);

		when(recycleBin.list(any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));
		mvc.perform(get("/api/recycle-bin").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.param("module", "TASKS")
			.param("deletedById", "4")
			.param("from", "2026-10-01")
			.param("sort", "name,asc")).andExpect(status().isOk());
		verify(recycleBin).list(
				argThat(f -> "TASKS".equals(f.module()) && f.deletedById() == 4L && f.from().toString().equals("2026-10-01")),
				argThat(p -> p.getSort().getOrderFor("label") != null), argThat(AuthenticatedUser::isSuperAdmin));

		mvc.perform(get("/api/recycle-bin").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.param("sort", "secret,asc")).andExpect(status().isBadRequest());
	}

	@Test
	void aNormalEmployeeIsForbiddenEverywhere() throws Exception {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SliceAuth.USER_ADMIN)) {
			mvc.perform(get("/api/recycle-bin").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
			mvc.perform(get("/api/recycle-bin/catalog").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
			mvc.perform(post("/api/recycle-bin/9/restore").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
			mvc.perform(post("/api/recycle-bin/9/purge").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"confirm\":\"TSK-000042\"}")).andExpect(status().isForbidden());
		}
		verifyNoInteractions(recycleBin);
	}

	@Test
	void theSuperAdminRestores() throws Exception {
		when(recycleBin.restore(eq(9L), any())).thenReturn(new Restored("TASK", 42L, "TSK-000042"));
		mvc.perform(post("/api/recycle-bin/9/restore").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.entityId").value(42));
	}

	@Test
	void anEmployeeGrantedThePermissionGetsIn() throws Exception {
		AuthenticatedUser granted = new AuthenticatedUser(13L, "bin@teamops.local", "Bin Keeper", 5L,
				Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "RECYCLE_BIN_MANAGE"));
		when(recycleBin.restore(eq(9L), any())).thenReturn(new Restored("TASK", 42L, "TSK-000042"));
		mvc.perform(post("/api/recycle-bin/9/restore").header(HttpHeaders.AUTHORIZATION, bearer(granted)))
			.andExpect(status().isOk());
	}

	@Test
	void permanentDeleteNeedsTheName() throws Exception {
		mvc.perform(post("/api/recycle-bin/9/purge").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\" \"}")).andExpect(status().isBadRequest());
		verifyNoInteractions(recycleBin);

		mvc.perform(post("/api/recycle-bin/9/purge").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"TSK-000042\"}")).andExpect(status().isNoContent());
		verify(recycleBin).purge(eq(9L), eq("TSK-000042"), argThat(AuthenticatedUser::isSuperAdmin));
	}

}
