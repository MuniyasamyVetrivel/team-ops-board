package com.teamops.ticket.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.teamops.ticket.dto.TicketDtos.CategoryResponse;
import com.teamops.ticket.dto.TicketDtos.MyTicketSummary;
import com.teamops.ticket.dto.TicketDtos.TicketDetail;
import com.teamops.ticket.dto.TicketDtos.TicketListItem;
import com.teamops.ticket.dto.TicketRequests;
import com.teamops.ticket.dto.TicketView;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.service.TicketCollaborationService;
import com.teamops.ticket.service.TicketService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Help desk API. {@code @PreAuthorize} checks the permission; the services check who may see and work on each
 * ticket (requester, assignee, department agents and managers).
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TICKET_VIEW')")
public class TicketController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("created", List.of("createdAt", "id"), "updated",
			List.of("updatedAt"), "priority", List.of("priorityRank", "createdAt"), "status",
			List.of("statusRank", "createdAt"), "due", List.of("resolutionDueAt", "id"), "code", List.of("id"),
			"subject", List.of("subject"));

	private final TicketService ticketService;

	private final TicketCollaborationService collaborationService;

	@GetMapping
	public PageResponse<TicketListItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<TicketStatus> status,
			@RequestParam(required = false) Set<TicketPriority> priority,
			@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Long assigneeId, @RequestParam(defaultValue = "ALL") TicketView view,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser actor) {
		var criteria = new TicketRequests.Search(search, status, priority, categoryId, departmentId, assigneeId, view);
		return ticketService.search(criteria, PageRequests.of(page, size, sort, SORT_FIELDS, "created,desc"), actor);
	}

	@GetMapping("/my/summary")
	public MyTicketSummary mySummary(@AuthenticationPrincipal AuthenticatedUser actor) {
		return ticketService.mySummary(actor);
	}

	@GetMapping("/categories")
	public List<CategoryResponse> categories() {
		return ticketService.categories();
	}

	@GetMapping("/{id}")
	public TicketDetail get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		return ticketService.get(id, actor);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('TICKET_CREATE')")
	public ResponseEntity<TicketDetail> create(@Valid @RequestBody TicketRequests.CreateTicket request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ticketService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('TICKET_EDIT')")
	public TicketDetail update(@PathVariable Long id, @Valid @RequestBody TicketRequests.UpdateTicket request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return ticketService.update(id, request, actor);
	}

	@PutMapping("/{id}/assignee")
	@PreAuthorize("hasAuthority('TICKET_EDIT')")
	public TicketDetail assign(@PathVariable Long id, @RequestBody TicketRequests.Assign request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ticketService.assign(id, request.assigneeId(), actor, ClientInfo.from(http));
	}

	/** Agents change any status; requesters may only confirm (close) or reopen a resolved ticket. */
	@PutMapping("/{id}/status")
	public TicketDetail changeStatus(@PathVariable Long id, @Valid @RequestBody TicketRequests.ChangeStatus request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ticketService.changeStatus(id, request.status(), actor, ClientInfo.from(http));
	}

	// --- replies --------------------------------------------------------------------------------------------

	@PostMapping("/{id}/comments")
	public TicketDetail addComment(@PathVariable Long id, @Valid @RequestBody TicketRequests.Comment request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.addComment(id, request.body(), request.internal(), actor);
	}

	@PutMapping("/{id}/comments/{commentId}")
	public TicketDetail editComment(@PathVariable Long id, @PathVariable Long commentId,
			@Valid @RequestBody TicketRequests.EditComment request, @AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.editComment(id, commentId, request.body(), actor);
	}

	@DeleteMapping("/{id}/comments/{commentId}")
	public TicketDetail deleteComment(@PathVariable Long id, @PathVariable Long commentId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.deleteComment(id, commentId, actor);
	}

	// --- attachments ----------------------------------------------------------------------------------------

	@PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public TicketDetail addAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.addAttachment(id, file, actor);
	}

	/** Always served as a download, with the content type derived from the validated extension. */
	@GetMapping("/{id}/attachments/{fileId}")
	public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		TicketCollaborationService.Download download = collaborationService.download(id, fileId, actor);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.contentType()))
			.contentLength(download.sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(download.content());
	}

	@DeleteMapping("/{id}/attachments/{fileId}")
	public TicketDetail deleteAttachment(@PathVariable Long id, @PathVariable Long fileId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.deleteAttachment(id, fileId, actor);
	}

}
