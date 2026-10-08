package com.teamops.marketing.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.csv.CsvImportService;
import com.teamops.common.csv.ImportDtos.ImportDefinition;
import com.teamops.common.csv.ImportDtos.ImportPreview;
import com.teamops.common.csv.ImportDtos.ImportResult;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Marketing CSV imports: validate and preview first, then commit. Each import type also requires its own edit
 * permission (e.g. SEO_EDIT), which {@link CsvImportService} checks.
 */
@RestController
@RequestMapping("/api/marketing/imports")
@PreAuthorize("hasAuthority('MARKETING_VIEW')")
@RequiredArgsConstructor
public class MarketingImportController {

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final CsvImportService importService;

	@GetMapping
	public List<ImportDefinition> definitions(@AuthenticationPrincipal AuthenticatedUser actor) {
		return importService.definitions(actor);
	}

	/** A header row and an example row, always served as a download. */
	@GetMapping("/{type}/template")
	public ResponseEntity<byte[]> template(@PathVariable String type,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		byte[] body = importService.template(type, actor);
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(type + "-template.csv").build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(body);
	}

	/** Validates the file and returns valid rows, invalid rows and their errors. Saves nothing. */
	@PostMapping(path = "/{type}/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportPreview preview(@PathVariable String type, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return importService.preview(type, file, actor);
	}

	/**
	 * Imports the previewed file. {@code checksum} comes from the preview. When the file has invalid rows,
	 * {@code skipInvalid} must be true, and those rows are left out.
	 */
	@PostMapping(path = "/{type}/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportResult commit(@PathVariable String type, @RequestPart("file") MultipartFile file,
			@RequestParam String checksum, @RequestParam(defaultValue = "false") boolean skipInvalid,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
		return importService.commit(type, file, checksum, skipInvalid, actor, ClientInfo.from(request));
	}

}
