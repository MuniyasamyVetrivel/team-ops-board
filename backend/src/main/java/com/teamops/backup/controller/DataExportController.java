package com.teamops.backup.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.backup.service.DataExportService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * "Export all data" (DATA_EXPORT, Super Admin only by default; 403 otherwise): a ZIP of every table as CSV plus all
 * uploaded files, streamed as an attachment.
 */
@RestController
@PreAuthorize("hasAuthority('DATA_EXPORT')")
@RequestMapping("/api/admin/data-export")
@RequiredArgsConstructor
public class DataExportController {

	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
		.withZone(ZoneOffset.UTC);

	private final DataExportService exports;

	private final Clock clock;

	@GetMapping
	public void export(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		String fileName = "team-ops-export-" + FILE_TIME.format(clock.instant()) + ".zip";
		response.setContentType("application/zip");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString());
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		exports.export(response.getOutputStream(), actor.id(), ClientInfo.from(request));
	}

}
