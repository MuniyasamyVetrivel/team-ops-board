package com.teamops.module.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.module.dto.ModuleDtos.ModuleSettings;
import com.teamops.module.dto.ModuleDtos.Tool;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.module.service.ModuleAdminService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/**
 * Module settings need MODULE_MANAGE (Super Admin only by default); the sidebar list is for everyone signed in; and
 * every request under /api/modules/{key}/ passes the module check first (view for reads, edit for the rest), even on
 * an endpoint without {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = { ModuleAdminController.class, DepartmentToolsController.class })
@SecuritySliceTest
@Import(ModuleApiSecurityTest.ProbeController.class)
class ModuleApiSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private ModuleAccessService moduleAccess;

	@MockitoBean
	private ModuleAdminService moduleAdmin;

	@Autowired
	private ProbeController probe;

	@BeforeEach
	void resetProbe() {
		probe.calls.set(0);
	}

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void moduleSettingsAreForModuleManageHoldersOnly() throws Exception {
		mvc.perform(get("/api/admin/modules")).andExpect(status().isUnauthorized());
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SliceAuth.USER_ADMIN, SliceAuth.NOBODY)) {
			mvc.perform(get("/api/admin/modules").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
			mvc.perform(put("/api/admin/modules/bench/departments/3").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":true}")).andExpect(status().isForbidden());
		}
		verifyNoInteractions(moduleAdmin);
	}

	@Test
	void theSuperAdminSwitchesModulesPerDepartment() throws Exception {
		ModuleSettings bench = new ModuleSettings("bench", "Bench Management", "Users", "", "BENCH_VIEW",
				"BENCH_EDIT", List.of());
		when(moduleAdmin.list()).thenReturn(List.of(bench));
		when(moduleAdmin.setEnabled(eq("bench"), eq(3L), eq(true), any(), any())).thenReturn(bench);
		String token = bearer(SliceAuth.SUPER_ADMIN);

		mvc.perform(get("/api/admin/modules").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].key").value("bench"))
			.andExpect(jsonPath("$[0].viewPermission").value("BENCH_VIEW"));
		mvc.perform(put("/api/admin/modules/bench/departments/3").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.key").value("bench"));
		mvc.perform(put("/api/admin/modules/bench/departments/3").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isBadRequest());
		verify(moduleAdmin).setEnabled(eq("bench"), eq(3L), eq(true), eq(SliceAuth.SUPER_ADMIN), any());
	}

	@Test
	void everyoneSignedInGetsTheirOwnSidebarTools() throws Exception {
		when(moduleAccess.toolsFor(SliceAuth.EMPLOYEE))
			.thenReturn(List.of(new Tool("bench", "Bench Management", "Users", "", false)));

		mvc.perform(get("/api/department-tools")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/department-tools").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].key").value("bench"))
			.andExpect(jsonPath("$[0].canEdit").value(false));
	}

	@Test
	void moduleEndpointsCheckViewForReadsAndEditForChanges() throws Exception {
		String token = bearer(SliceAuth.EMPLOYEE);

		mvc.perform(get("/api/modules/probe/items").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk());
		verify(moduleAccess).requireAccess(SliceAuth.EMPLOYEE, "probe", false);

		mvc.perform(post("/api/modules/probe/items").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk());
		mvc.perform(delete("/api/modules/probe/items").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk());
		verify(moduleAccess, times(2))
			.requireAccess(SliceAuth.EMPLOYEE, "probe", true);
	}

	@Test
	void aRefusedModuleRequestNeverReachesTheController() throws Exception {
		when(moduleAccess.requireAccess(any(), eq("probe"), anyBoolean()))
			.thenThrow(ApiException.forbidden("MODULE_DISABLED", "Probe is not switched on for your department"));
		when(moduleAccess.requireAccess(any(), eq("nothing"), anyBoolean()))
			.thenThrow(ApiException.notFound("MODULE_NOT_FOUND", "Unknown module: nothing"));

		mvc.perform(get("/api/modules/probe/items")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/modules/probe/items").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		mvc.perform(post("/api/modules/probe/items").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/modules/nothing/items").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
		assertThat(probe.calls.get()).isZero();
	}

	/** A department tool endpoint with no {@code @PreAuthorize} of its own. */
	@RestController
	@RequestMapping("/api/modules/probe/items")
	static class ProbeController {

		final AtomicInteger calls = new AtomicInteger();

		@GetMapping
		List<String> list() {
			calls.incrementAndGet();
			return List.of("item");
		}

		@PostMapping
		void create() {
			calls.incrementAndGet();
		}

		@DeleteMapping
		void remove() {
			calls.incrementAndGet();
		}

	}

}
