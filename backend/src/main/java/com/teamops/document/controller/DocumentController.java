package com.teamops.document.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.document.dto.DocumentDtos.DocumentDetail;
import com.teamops.document.dto.DocumentDtos.DocumentItem;
import com.teamops.document.dto.DocumentDtos.UpdateDocument;
import com.teamops.document.service.DocumentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Documents: DOCUMENT_VIEW reads; DOCUMENT_EDIT uploads and manages within the actor's scope. */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DOCUMENT_VIEW')")
public class DocumentController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("updated", List.of("updatedAt"), "name",
			List.of("name"), "created", List.of("createdAt", "id"));

	private final DocumentService documentService;

	@GetMapping
	public PageResponse<DocumentItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long projectId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser actor) {
		return documentService.search(search, departmentId, projectId,
				PageRequests.of(page, size, sort, SORT_FIELDS, "updated,desc"), actor);
	}

	@GetMapping("/{id}")
	public DocumentDetail get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		return documentService.get(id, actor);
	}

	/** {@code departmentId} omitted = company-wide (Super Admin only). */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('DOCUMENT_EDIT')")
	public ResponseEntity<DocumentDetail> upload(@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String name, @RequestParam(required = false) String description,
			@RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long projectId,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(documentService.upload(file, name, description, departmentId, projectId, actor,
					ClientInfo.from(http)));
	}

	@PostMapping(path = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('DOCUMENT_EDIT')")
	public DocumentDetail addVersion(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String changeNote, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return documentService.addVersion(id, file, changeNote, actor, ClientInfo.from(http));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('DOCUMENT_EDIT')")
	public DocumentDetail update(@PathVariable Long id, @Valid @RequestBody UpdateDocument request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return documentService.update(id, request, actor);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('DOCUMENT_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		documentService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	/** Always served as a download. */
	@GetMapping("/{id}/versions/{versionNo}/download")
	public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable int versionNo,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		DocumentService.Download download = documentService.download(id, versionNo, actor);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.contentType()))
			.contentLength(download.sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(download.content());
	}

}
