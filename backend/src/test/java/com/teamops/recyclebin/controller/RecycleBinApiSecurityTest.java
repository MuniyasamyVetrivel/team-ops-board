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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.common.web.PageResponse;
import com.teamops.recyclebin.dto.RecycleBinDtos.Restored;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** Recycle bin API: signed-in users only, scope checked by the service, permanent delete by Super Admins with a name. */
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
	void aViewerOutsideTheBinIsRefusedByTheService() throws Exception {
		when(recycleBin.list(any(), any(), argThat(u -> u.id().equals(SliceAuth.EMPLOYEE.id()))))
			.thenThrow(ApiException.forbidden("RECYCLE_BIN_FORBIDDEN", "No"));
		mvc.perform(get("/api/recycle-bin").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RECYCLE_BIN_FORBIDDEN"));
	}

	@Test
	void restoreIsForAnyoneTheServiceAllows() throws Exception {
		when(recycleBin.restore(eq(9L), any())).thenReturn(new Restored("TASK", 42L, "TSK-000042"));
		mvc.perform(post("/api/recycle-bin/9/restore").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.entityId").value(42));
	}

	@Test
	void permanentDeleteIsForSuperAdminsAndNeedsTheName() throws Exception {
		mvc.perform(post("/api/recycle-bin/9/purge").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.USER_ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"TSK-000042\"}")).andExpect(status().isForbidden());
		verifyNoInteractions(recycleBin);

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
