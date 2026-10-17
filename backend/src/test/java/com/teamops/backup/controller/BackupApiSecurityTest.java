package com.teamops.backup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.backup.dto.BackupDtos.CopyStatus;
import com.teamops.backup.dto.BackupDtos.Problem;
import com.teamops.backup.dto.BackupDtos.Run;
import com.teamops.backup.dto.BackupDtos.Status;
import com.teamops.backup.service.BackupService;
import com.teamops.backup.service.DataExportService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.common.web.PageResponse;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/**
 * Backup status and history need BACKUP_VIEW, "Run backup now" the Super Admin role, "Export all data" DATA_EXPORT;
 * all Super Admin only by default, 403 for everyone else.
 */
@WebMvcTest(controllers = { BackupController.class, DataExportController.class })
@SecuritySliceTest
class BackupApiSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private BackupService backups;

	@MockitoBean
	private DataExportService exports;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void everyoneButTheSuperAdminIsForbidden() throws Exception {
		mvc.perform(get("/api/admin/backups/status")).andExpect(status().isUnauthorized());
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SliceAuth.USER_ADMIN, SliceAuth.NOBODY)) {
			mvc.perform(get("/api/admin/backups/status").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
			mvc.perform(get("/api/admin/backups").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
			mvc.perform(post("/api/admin/backups/run").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
			mvc.perform(get("/api/admin/data-export").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isForbidden());
		}
		verifyNoInteractions(backups, exports);
	}

	@Test
	void theSuperAdminSeesTheStatusAndHistory() throws Exception {
		Problem stale = new Problem("SECONDARY_STALE", "The last second copy is older than 26 hours");
		when(backups.status()).thenReturn(new Status(false, List.of(stale),
				List.of(new CopyStatus("PRIMARY", "LOCAL D:\\team-ops-backups", true, List.of(), null, "SUCCESS", null),
						new CopyStatus("SECONDARY", "S3 copy/backups/", false, List.of(stale), null, "FAILED", "denied")),
				null, null, false, true, "0 0 2 * * *", "Asia/Kolkata", Instant.parse("2026-10-16T20:30:00Z"),
				"LOCAL D:\\team-ops-backups", "S3 copy/backups/", 30, 12, 26));
		mvc.perform(get("/api/admin/backups/status").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.healthy").value(false))
			.andExpect(jsonPath("$.problems[0].code").value("SECONDARY_STALE"))
			.andExpect(jsonPath("$.copies[1].copy").value("SECONDARY"))
			.andExpect(jsonPath("$.copies[1].healthy").value(false))
			.andExpect(jsonPath("$.secondaryTarget").value("S3 copy/backups/"))
			.andExpect(jsonPath("$.keepDaily").value(30));

		when(backups.history(any())).thenReturn(new PageResponse<>(List.of(), 0, 30, 0, 0));
		mvc.perform(get("/api/admin/backups").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isOk());
		mvc.perform(get("/api/admin/backups").param("sort", "secret,asc")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))).andExpect(status().isBadRequest());
	}

	@Test
	void theSuperAdminStartsABackup() throws Exception {
		when(backups.startNow(1L)).thenReturn(new Run(5L, "MANUAL", "RUNNING", Instant.parse("2026-10-16T10:00:00Z"),
				null, null, null, null, "LOCAL D:\\team-ops-backups", null, null, 1L, null, null));
		mvc.perform(post("/api/admin/backups/run").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("RUNNING"));
	}

	@Test
	void aGrantedViewerSeesStatusButCannotRunOrExport() throws Exception {
		AuthenticatedUser viewer = new AuthenticatedUser(21L, "ops@teamops.local", "Ops Viewer", 5L,
				Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "BACKUP_VIEW"));
		when(backups.history(any())).thenReturn(new PageResponse<>(List.of(), 0, 30, 0, 0));
		mvc.perform(get("/api/admin/backups").header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
			.andExpect(status().isOk());
		mvc.perform(post("/api/admin/backups/run").header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/data-export").header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(exports);
	}

	@Test
	void aGrantedExporterDownloadsTheZipAsAnAttachment() throws Exception {
		AuthenticatedUser exporter = new AuthenticatedUser(22L, "exporter@teamops.local", "Exporter", 5L,
				Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "DATA_EXPORT"));
		doAnswer(inv -> {
			inv.<OutputStream>getArgument(0).write(new byte[] { 'P', 'K' });
			return null;
		}).when(exports).export(any(), eq(22L), eq(false), any());
		mvc.perform(get("/api/admin/data-export").header(HttpHeaders.AUTHORIZATION, bearer(exporter)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
					org.hamcrest.Matchers.startsWith("attachment; filename=\"team-ops-export-")));
		// Without FINANCE_VIEW the export leaves finance data out.
		verify(exports).export(any(), eq(22L), eq(false), any());
		mvc.perform(get("/api/admin/backups/status").header(HttpHeaders.AUTHORIZATION, bearer(exporter)))
			.andExpect(status().isForbidden());
	}

}
