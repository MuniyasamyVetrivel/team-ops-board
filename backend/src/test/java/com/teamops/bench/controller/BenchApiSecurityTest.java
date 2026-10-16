package com.teamops.bench.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.teamops.bench.dto.BenchDtos.ConsultantDto;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.service.ClientService;
import com.teamops.bench.service.ConsultantService;
import com.teamops.bench.service.ConsultantStatus;
import com.teamops.bench.service.EngagementService;
import com.teamops.common.csv.CsvImportService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.common.web.PageResponse;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/**
 * Bench Management is BENCH_VIEW to read and BENCH_EDIT to change (Super Admin only by default), on top of the module
 * switch that guards every request under /api/modules/bench/.
 */
@WebMvcTest(controllers = { ConsultantController.class, ClientController.class, EngagementController.class,
		BenchImportController.class })
@SecuritySliceTest
class BenchApiSecurityTest {

	/** Read-only access, as a Pre-Sales grant would give. */
	private static final AuthenticatedUser VIEWER = new AuthenticatedUser(12L, "presales@teamops.local", "Pre Sales",
			5L, Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "BENCH_VIEW"));

	private static final String CONSULTANT_BODY = "{\"employeeId\":7,\"skills\":[\"Tosca\"]}";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private ModuleAccessService moduleAccess;

	@MockitoBean
	private ConsultantService consultants;

	@MockitoBean
	private ClientService clients;

	@MockitoBean
	private EngagementService engagements;

	@MockitoBean
	private CsvImportService imports;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void nobodyWithoutBenchPermissionsGetsIn() throws Exception {
		mvc.perform(get("/api/modules/bench/consultants")).andExpect(status().isUnauthorized());
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SliceAuth.USER_ADMIN, SliceAuth.NOBODY)) {
			String token = bearer(user);
			for (MockHttpServletRequestBuilder request : List.of(get("/api/modules/bench/consultants"),
					get("/api/modules/bench/consultants/3"), get("/api/modules/bench/consultants/export"),
					get("/api/modules/bench/clients"), get("/api/modules/bench/engagements"),
					get("/api/modules/bench/imports/consultants/template"),
					post("/api/modules/bench/consultants").contentType(MediaType.APPLICATION_JSON).content(CONSULTANT_BODY),
					delete("/api/modules/bench/clients/3"))) {
				mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(consultants, clients, engagements, imports);
	}

	@Test
	void benchViewReadsButOnlyBenchEditChangesOrImports() throws Exception {
		when(consultants.search(any(), any())).thenReturn(new PageResponse<>(List.of(consultant()), 0, 25, 1, 1));
		when(consultants.export(any())).thenReturn("employee_id\r\n".getBytes());
		String token = bearer(VIEWER);

		mvc.perform(get("/api/modules/bench/consultants").param("status", "ON_BENCH")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].status").value("ON_BENCH"))
			.andExpect(jsonPath("$.content[0].skills[0]").value("Tosca"));
		mvc.perform(get("/api/modules/bench/consultants/export").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"consultants.csv\""))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		verify(moduleAccess, atLeastOnce()).requireAccess(VIEWER, "bench", false);

		for (MockHttpServletRequestBuilder request : List.of(
				post("/api/modules/bench/consultants").contentType(MediaType.APPLICATION_JSON).content(CONSULTANT_BODY),
				put("/api/modules/bench/consultants/3").contentType(MediaType.APPLICATION_JSON).content(CONSULTANT_BODY),
				delete("/api/modules/bench/consultants/3"), get("/api/modules/bench/consultants/candidates"),
				post("/api/modules/bench/clients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}"),
				delete("/api/modules/bench/engagements/3"), get("/api/modules/bench/imports/consultants"))) {
			mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
		}
		verifyNoInteractions(clients, engagements, imports);
	}

	@Test
	void theSuperAdminChangesWithValidatedInput() throws Exception {
		when(consultants.create(any(), any(), any())).thenReturn(consultant());
		String token = bearer(SliceAuth.SUPER_ADMIN);

		mvc.perform(post("/api/modules/bench/consultants").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(CONSULTANT_BODY)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(30));
		mvc.perform(post("/api/modules/bench/consultants").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"experienceYears\":61}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/modules/bench/engagements").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"clientId\":1,\"name\":\" \"}")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/modules/bench/consultants").param("sort", "salary,desc")
			.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isBadRequest());
		verify(moduleAccess, atLeastOnce()).requireAccess(eq(SliceAuth.SUPER_ADMIN), eq("bench"), eq(true));
		verifyNoInteractions(engagements);
	}

	@Test
	void aModuleSwitchedOffNeverReachesTheServices() throws Exception {
		when(moduleAccess.requireAccess(any(), eq("bench"), anyBoolean()))
			.thenThrow(ApiException.forbidden("MODULE_DISABLED", "Bench Management is not switched on for your department"));
		String token = bearer(SliceAuth.SUPER_ADMIN);

		mvc.perform(get("/api/modules/bench/consultants").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		mvc.perform(post("/api/modules/bench/clients").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Acme\"}")).andExpect(status().isForbidden());
		verifyNoInteractions(consultants, clients, engagements, imports);
	}

	private static ConsultantDto consultant() {
		return new ConsultantDto(30L, 7L, null, null, null, null, EmploymentStatus.ACTIVE, ConsultantStatus.ON_BENCH,
				List.of("Tosca"), List.of(), null, 0, null, null);
	}

}
