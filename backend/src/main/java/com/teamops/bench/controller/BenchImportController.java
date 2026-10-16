package com.teamops.bench.controller;

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

import com.teamops.bench.BenchModule;
import com.teamops.bench.service.ConsultantCsvImporter;
import com.teamops.common.csv.CsvImportService;
import com.teamops.common.csv.CsvImporter.Area;
import com.teamops.common.csv.ImportDtos.ImportDefinition;
import com.teamops.common.csv.ImportDtos.ImportPreview;
import com.teamops.common.csv.ImportDtos.ImportResult;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Consultant CSV import through the shared import flow: validate and preview first, then commit. Lives under the
 * module's path, so the module switch applies as well as BENCH_EDIT.
 */
@RestController
@RequestMapping(BenchModule.API + "/imports/consultants")
@PreAuthorize("hasAuthority('BENCH_VIEW') and hasAuthority('BENCH_EDIT')")
@RequiredArgsConstructor
public class BenchImportController {

	private static final String TYPE = ConsultantCsvImporter.TYPE;

	private final CsvImportService importService;

	@GetMapping
	public ImportDefinition definition(@AuthenticationPrincipal AuthenticatedUser actor) {
		return importService.definitions(Area.BENCH, actor)
			.stream()
			.filter(definition -> definition.type().equals(TYPE))
			.findFirst()
			.orElseThrow();
	}

	/** A header row and an example row, always served as a download. */
	@GetMapping("/template")
	public ResponseEntity<byte[]> template(@AuthenticationPrincipal AuthenticatedUser actor) {
		return BenchCsv.download(TYPE + "-template.csv", importService.template(Area.BENCH, TYPE, actor));
	}

	/** Validates the file and returns valid rows, invalid rows and their errors. Saves nothing. */
	@PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportPreview preview(@RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return importService.preview(Area.BENCH, TYPE, file, actor);
	}

	/**
	 * Imports the previewed file. {@code checksum} comes from the preview. When the file has invalid rows,
	 * {@code skipInvalid} must be true, and those rows are left out.
	 */
	@PostMapping(path = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportResult commit(@RequestPart("file") MultipartFile file, @RequestParam String checksum,
			@RequestParam(defaultValue = "false") boolean skipInvalid, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest request) {
		return importService.commit(Area.BENCH, TYPE, file, checksum, skipInvalid, actor, ClientInfo.from(request));
	}

}
