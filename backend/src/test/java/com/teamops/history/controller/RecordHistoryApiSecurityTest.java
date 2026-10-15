package com.teamops.history.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.common.web.PageResponse;
import com.teamops.history.service.RecordHistoryService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/** The History tab endpoint: signed-in users only, read-only, scoped by the service (404 when hidden). */
@WebMvcTest(controllers = RecordHistoryController.class)
@SecuritySliceTest
class RecordHistoryApiSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private RecordHistoryService historyService;

	@Test
	void historyNeedsASignedInUserAndPassesThemOn() throws Exception {
		mvc.perform(get("/api/history/TASK/42")).andExpect(status().isUnauthorized());
		verifyNoInteractions(historyService);

		when(historyService.history(eq("TASK"), eq(42L), any(), any()))
			.thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));
		mvc.perform(get("/api/history/TASK/42").param("size", "500")
			.header(HttpHeaders.AUTHORIZATION, bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
		verify(historyService).history(eq("TASK"), eq(42L), argThat(page -> page.getPageSize() <= 100),
				argThat(viewer -> viewer.id().equals(SliceAuth.EMPLOYEE.id())));
	}

	@Test
	void aHiddenRecordAnswersNotFound() throws Exception {
		when(historyService.history(eq("TASK"), eq(7L), any(), any()))
			.thenThrow(ApiException.notFound("RECORD_NOT_FOUND", "Record not found"));
		mvc.perform(get("/api/history/TASK/7").header(HttpHeaders.AUTHORIZATION, bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
	}

	@Test
	void historyCannotBeChangedOrDeleted() throws Exception {
		mvc.perform(delete("/api/history/TASK/42").header(HttpHeaders.AUTHORIZATION, bearer()))
			.andExpect(status().isMethodNotAllowed());
		mvc.perform(put("/api/history/TASK/42").header(HttpHeaders.AUTHORIZATION, bearer()))
			.andExpect(status().isMethodNotAllowed());
	}

	private String bearer() {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, SliceAuth.EMPLOYEE);
	}

}
