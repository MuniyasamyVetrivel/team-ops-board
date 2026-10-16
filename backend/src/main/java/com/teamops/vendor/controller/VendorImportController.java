package com.teamops.vendor.controller;

import java.util.List;

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
import com.teamops.common.csv.CsvImporter.Area;
import com.teamops.common.csv.ImportDtos.ImportDefinition;
import com.teamops.common.csv.ImportDtos.ImportPreview;
import com.teamops.common.csv.ImportDtos.ImportResult;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.vendor.VendorModule;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Vendor and contract CSV imports ({@code vendors}, {@code vendor-contracts}) through the shared import flow: validate
 * and preview first, then commit. Lives under the module's path, so the module switch applies as well as
 * VENDOR_EDIT.
 */
@RestController
@RequestMapping(VendorModule.API + "/imports")
@PreAuthorize("hasAuthority('VENDOR_VIEW') and hasAuthority('VENDOR_EDIT')")
@RequiredArgsConstructor
public class VendorImportController {

	private final CsvImportService importService;

	@GetMapping
	public List<ImportDefinition> definitions(@AuthenticationPrincipal AuthenticatedUser actor) {
		return importService.definitions(Area.VENDORS, actor);
	}

	/** A header row and an example row, always served as a download. */
	@GetMapping("/{type}/template")
	public ResponseEntity<byte[]> template(@PathVariable String type, @AuthenticationPrincipal AuthenticatedUser actor) {
		return VendorDownloads.csv(type + "-template.csv", importService.template(Area.VENDORS, type, actor));
	}

	/** Validates the file and returns valid rows, invalid rows and their errors. Saves nothing. */
	@PostMapping(path = "/{type}/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportPreview preview(@PathVariable String type, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return importService.preview(Area.VENDORS, type, file, actor);
	}

	/**
	 * Imports the previewed file. {@code checksum} comes from the preview. When the file has invalid rows,
	 * {@code skipInvalid} must be true, and those rows are left out.
	 */
	@PostMapping(path = "/{type}/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportResult commit(@PathVariable String type, @RequestPart("file") MultipartFile file,
			@RequestParam String checksum, @RequestParam(defaultValue = "false") boolean skipInvalid,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
		return importService.commit(Area.VENDORS, type, file, checksum, skipInvalid, actor, ClientInfo.from(request));
	}

}
