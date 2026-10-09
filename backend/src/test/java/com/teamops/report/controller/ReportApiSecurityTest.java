package com.teamops.report.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.report.CsvReportExporter;
import com.teamops.common.report.ReportExporters;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.report.dto.ReportDtos.Range;
import com.teamops.report.dto.ReportDtos.TaskReport;
import com.teamops.report.dto.ReportDtos.TaskSummary;
import com.teamops.report.service.ReportService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;
import com.teamops.task.entity.TaskStatus;

/** Management reports: REPORT_VIEW reads, REPORT_EXPORT downloads, plus the module's own view permission. */
@WebMvcTest(controllers = ReportController.class)
@SecuritySliceTest
@Import({ ReportExporters.class, CsvReportExporter.class })
class ReportApiSecurityTest {

	private static final AuthenticatedUser MANAGER = new AuthenticatedUser(20L, "manager@teamops.local", "Manager", 3L,
			Set.of("DEPARTMENT_MANAGER"), Set.of("TASK_VIEW", "WORKLOAD_VIEW", "TICKET_VIEW", "PROJECT_VIEW", "REPORT_VIEW",
					"REPORT_EXPORT"));

	/** Can read reports but not download them, and has no ticket access. */
	private static final AuthenticatedUser READER = new AuthenticatedUser(21L, "reader@teamops.local", "Reader", 3L,
			Set.of("DEPARTMENT_MANAGER"), Set.of("TASK_VIEW", "REPORT_VIEW"));

	private static final Range RANGE = new Range(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 9));

	private static final TaskReport REPORT = new TaskReport(RANGE,
			new TaskSummary(12, 8, 6, 5, 83, 10, 2, 20, new BigDecimal("31.5")), List.of(), List.of(), List.of(), List.of());

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private ReportService reportService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void reportsNeedReportView() throws Exception {
		for (String path : List.of("/api/reports/tasks", "/api/reports/workload", "/api/reports/tickets", "/api/reports/projects")) {
			mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
			mvc.perform(get(path + "/export").header(HttpHeaders.AUTHORIZATION, bearer(READER))).andExpect(status().isForbidden());
		}
		// A report also needs its module's view permission.
		mvc.perform(get("/api/reports/tickets").header(HttpHeaders.AUTHORIZATION, bearer(READER))).andExpect(status().isForbidden());
		verifyNoInteractions(reportService);
	}

	@Test
	void filtersArePassedOn() throws Exception {
		when(reportService.range(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(RANGE);
		when(reportService.tasks(any(), any(), any(), any(), any(), any())).thenReturn(REPORT);

		mvc.perform(get("/api/reports/tasks").param("from", "2026-09-01")
			.param("to", "2026-09-30")
			.param("departmentId", "3")
			.param("userId", "4")
			.param("projectId", "5")
			.param("status", "TODO", "BLOCKED")
			.header(HttpHeaders.AUTHORIZATION, bearer(READER)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.onTimePct").value(83));
		verify(reportService).tasks(RANGE, 3L, 4L, 5L, Set.of(TaskStatus.TODO, TaskStatus.BLOCKED), READER);
		mvc.perform(get("/api/reports/tasks").param("from", "not-a-date").header(HttpHeaders.AUTHORIZATION, bearer(READER)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void exportsAreCsvAttachmentsAndPdfIsNotAvailableYet() throws Exception {
		when(reportService.range(isNull(), isNull())).thenReturn(RANGE);
		when(reportService.tasks(eq(RANGE), any(), any(), any(), any(), eq(MANAGER))).thenReturn(REPORT);

		mvc.perform(get("/api/reports/tasks/export").header(HttpHeaders.AUTHORIZATION, bearer(MANAGER)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("task-report-2026-09-10-to-2026-10-09.csv")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mvc.perform(get("/api/reports/tasks/export").param("format", "PDF").header(HttpHeaders.AUTHORIZATION, bearer(MANAGER)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FORMAT_NOT_AVAILABLE"));
	}

}
