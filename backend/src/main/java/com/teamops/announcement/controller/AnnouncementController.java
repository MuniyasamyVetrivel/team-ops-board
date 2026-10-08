package com.teamops.announcement.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import com.teamops.announcement.dto.AnnouncementDtos.AnnouncementItem;
import com.teamops.announcement.dto.AnnouncementDtos.Save;
import com.teamops.announcement.dto.AnnouncementDtos.UnreadCount;
import com.teamops.announcement.entity.AnnouncementState;
import com.teamops.announcement.service.AnnouncementService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Announcements: everyone with DASHBOARD_VIEW reads; ANNOUNCEMENT_MANAGE publishes within the actor's scope. */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
public class AnnouncementController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("published", List.of("publishAt", "id"));

	private final AnnouncementService announcementService;

	@GetMapping
	public PageResponse<AnnouncementItem> list(@RequestParam(defaultValue = "ACTIVE") AnnouncementState state,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return announcementService.list(state, PageRequests.of(page, size, null, SORT_FIELDS, "published,desc"), actor);
	}

	@GetMapping("/unread-count")
	public UnreadCount unreadCount(@AuthenticationPrincipal AuthenticatedUser actor) {
		return announcementService.unreadCount(actor);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	public ResponseEntity<AnnouncementItem> create(@Valid @RequestBody Save request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(announcementService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	public AnnouncementItem update(@PathVariable Long id, @Valid @RequestBody Save request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return announcementService.update(id, request, actor);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		announcementService.delete(id, actor);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/read")
	public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		announcementService.markRead(id, actor);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/acknowledge")
	public ResponseEntity<Void> acknowledge(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		announcementService.acknowledge(id, actor);
		return ResponseEntity.noContent().build();
	}

}
