package com.teamops.recyclebin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.teamops.recyclebin.dto.RecycleBinDtos.RestoreCheck;
import com.teamops.recyclebin.dto.RecycleBinDtos.Restored;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/**
 * Recycle bin API: RECYCLE_BIN_MANAGE (Super Admin by default; 403 for everyone else) for viewing and restoring;
 * permanent delete also needs RECYCLE_BIN_PURGE and must name the item.
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
			mvc.perform(get("/api/recycle-bin/9/restore-check").header(HttpHeaders.AUTHORIZATION, bearer(user)))
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
	void theRestoreCheckReportsClashes() throws Exception {
		when(recycleBin.restoreCheck(eq(9L), any())).thenReturn(new RestoreCheck(false, "RESTORE_CONFLICT",
				"Another employee now has the employee ID E-7", List.of("The work email a@b.c now belongs to E-8")));
		mvc.perform(get("/api/recycle-bin/9/restore-check").header(HttpHeaders.AUTHORIZATION,
				bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canRestore").value(false))
			.andExpect(jsonPath("$.blockCode").value("RESTORE_CONFLICT"))
			.andExpect(jsonPath("$.warnings[0]").value("The work email a@b.c now belongs to E-8"));
	}

	@Test
	void anEmployeeGrantedManageRestoresButCannotDeletePermanently() throws Exception {
		AuthenticatedUser granted = new AuthenticatedUser(13L, "bin@teamops.local", "Bin Keeper", 5L,
				Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "RECYCLE_BIN_MANAGE"));
		when(recycleBin.restore(eq(9L), any())).thenReturn(new Restored("TASK", 42L, "TSK-000042"));
		mvc.perform(post("/api/recycle-bin/9/restore").header(HttpHeaders.AUTHORIZATION, bearer(granted)))
			.andExpect(status().isOk());
		mvc.perform(post("/api/recycle-bin/9/purge").header(HttpHeaders.AUTHORIZATION, bearer(granted))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"TSK-000042\"}")).andExpect(status().isForbidden());
		verify(recycleBin, never()).purge(any(), any(), any());
	}

	@Test
	void purgeAloneDoesNotOpenTheBin() throws Exception {
		AuthenticatedUser purgeOnly = new AuthenticatedUser(14L, "purge@teamops.local", "Purge Only", 5L,
				Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "RECYCLE_BIN_PURGE"));
		mvc.perform(get("/api/recycle-bin").header(HttpHeaders.AUTHORIZATION, bearer(purgeOnly)))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/recycle-bin/9/purge").header(HttpHeaders.AUTHORIZATION, bearer(purgeOnly))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"TSK-000042\"}")).andExpect(status().isForbidden());
		verifyNoInteractions(recycleBin);
	}

	@Test
	void anEmployeeGrantedBothDeletesPermanently() throws Exception {
		AuthenticatedUser granted = new AuthenticatedUser(15L, "both@teamops.local", "Bin Cleaner", 5L,
				Set.of("EMPLOYEE"),
				SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "RECYCLE_BIN_MANAGE", "RECYCLE_BIN_PURGE"));
		mvc.perform(post("/api/recycle-bin/9/purge").header(HttpHeaders.AUTHORIZATION, bearer(granted))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"TSK-000042\"}")).andExpect(status().isNoContent());
		verify(recycleBin).purge(eq(9L), eq("TSK-000042"), any());
	}

	@Test
	void anySignedInUserMayAskToUndoTheirOwnDelete() throws Exception {
		mvc.perform(post("/api/recycle-bin/undo").contentType(MediaType.APPLICATION_JSON)
			.content("{\"entityType\":\"TASK\",\"entityId\":42}")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/recycle-bin/undo").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"entityType\":\"TASK\"}")).andExpect(status().isBadRequest());
		verifyNoInteractions(recycleBin);

		when(recycleBin.undo(eq("TASK"), eq(42L), any())).thenReturn(new Restored("TASK", 42L, "TSK-000042"));
		mvc.perform(post("/api/recycle-bin/undo").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"entityType\":\"TASK\",\"entityId\":42}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.label").value("TSK-000042"));
		verify(recycleBin).undo(eq("TASK"), eq(42L), argThat(u -> u.id() == 4L));
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
