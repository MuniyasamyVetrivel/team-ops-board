package com.teamops.celebration.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.celebration.dto.CelebrationDtos.Attachment;
import com.teamops.celebration.dto.CelebrationDtos.ImportantDayDto;
import com.teamops.celebration.dto.CelebrationDtos.SaveImportantDay;
import com.teamops.celebration.entity.DayRecurrence;
import com.teamops.celebration.entity.ImportantDayCategory;
import com.teamops.celebration.entity.Region;
import com.teamops.celebration.service.ImportantDayService;
import com.teamops.celebration.service.ImportantDayService.DayFilter;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Important days: Digital Marketing users (MARKETING_VIEW) read and export, CELEBRATIONS_EDIT changes. CSV import runs through
 * {@code /api/marketing/imports/important-days}.
 */
@RestController
@PreAuthorize("hasAuthority('MARKETING_VIEW')")
@RequestMapping("/api/celebrations/important-days")
@RequiredArgsConstructor
public class ImportantDayController {

	static final Map<String, List<String>> SORTS = Map.of("date", List.of("date", "name", "id"), "name",
			List.of("name", "id"), "updated", List.of("updatedAt", "id"));

	private final ImportantDayService importantDayService;

	@GetMapping
	public PageResponse<ImportantDayDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<ImportantDayCategory> category,
			@RequestParam(required = false) Set<Region> region, @RequestParam(required = false) DayRecurrence recurrence,
			@RequestParam(required = false) Integer year, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return importantDayService.search(new DayFilter(search, category, region, recurrence, year),
				PageRequests.of(page, size, sort, SORTS, "date,asc"), viewer);
	}

	/** CSV in the import's columns. */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<ImportantDayCategory> category,
			@RequestParam(required = false) Set<Region> region, @RequestParam(required = false) DayRecurrence recurrence,
			@RequestParam(required = false) Integer year) {
		return CelebrationDownloads.csv("important-days.csv",
				importantDayService.export(new DayFilter(search, category, region, recurrence, year)));
	}

	@GetMapping("/{id}")
	public ImportantDayDto get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return importantDayService.get(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public ResponseEntity<ImportantDayDto> create(@Valid @RequestBody SaveImportantDay request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(importantDayService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public ImportantDayDto update(@PathVariable Long id, @Valid @RequestBody SaveImportantDay request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return importantDayService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		importantDayService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/attachments")
	public List<Attachment> attachments(@PathVariable Long id) {
		return importantDayService.attachments(id);
	}

	@PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public List<Attachment> addAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return importantDayService.addAttachment(id, file, actor, ClientInfo.from(http));
	}

	/** Always served as a download. */
	@GetMapping("/{id}/attachments/{fileId}")
	public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId) {
		return CelebrationDownloads.file(importantDayService.download(id, fileId));
	}

	@DeleteMapping("/{id}/attachments/{fileId}")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public List<Attachment> deleteAttachment(@PathVariable Long id, @PathVariable Long fileId,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return importantDayService.deleteAttachment(id, fileId, actor, ClientInfo.from(http));
	}

}
