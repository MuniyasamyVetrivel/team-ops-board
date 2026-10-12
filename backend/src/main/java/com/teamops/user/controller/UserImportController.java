package com.teamops.user.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.csv.CsvImportService;
import com.teamops.common.csv.CsvImporter.Area;
import com.teamops.common.csv.ImportDtos.ImportDefinition;
import com.teamops.common.csv.ImportDtos.ImportPreview;
import com.teamops.common.csv.ImportDtos.ImportResult;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.user.service.UserCsvImporter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Admin → Users → Import CSV, through the shared import flow: validate and preview first, then commit. The commit's
 * result carries the new users' temporary passwords, once; they are not stored anywhere in plain text.
 */
@RestController
@RequestMapping("/api/users/import")
@PreAuthorize("hasAuthority('USER_MANAGE')")
@RequiredArgsConstructor
public class UserImportController {

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private static final String TYPE = UserCsvImporter.TYPE;

	private final CsvImportService importService;

	@GetMapping
	public ImportDefinition definition(@AuthenticationPrincipal AuthenticatedUser actor) {
		return importService.definitions(Area.USERS, actor)
			.stream()
			.filter(definition -> definition.type().equals(TYPE))
			.findFirst()
			.orElseThrow();
	}

	@GetMapping("/template")
	public ResponseEntity<byte[]> template(@AuthenticationPrincipal AuthenticatedUser actor) {
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(TYPE + "-template.csv").build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(importService.template(Area.USERS, TYPE, actor));
	}

	@PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportPreview preview(@RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return importService.preview(Area.USERS, TYPE, file, actor);
	}

	@PostMapping(path = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ImportResult> commit(@RequestPart("file") MultipartFile file, @RequestParam String checksum,
			@RequestParam(defaultValue = "false") boolean skipInvalid,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
		ImportResult result = importService.commit(Area.USERS, TYPE, file, checksum, skipInvalid, actor,
				ClientInfo.from(request));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(result);
	}

}
