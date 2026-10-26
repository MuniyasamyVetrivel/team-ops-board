package com.teamops.celebration.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.teamops.celebration.dto.CelebrationDtos.MarketingEventDto;
import com.teamops.celebration.dto.CelebrationDtos.SaveMarketingEvent;
import com.teamops.celebration.entity.MarketingEventStatus;
import com.teamops.celebration.entity.MarketingEventType;
import com.teamops.celebration.service.MarketingEventService;
import com.teamops.celebration.service.MarketingEventService.EventFilter;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Upcoming events, webinars, event marketing and email marketing: MARKETING_VIEW reads, CELEBRATIONS_EDIT changes. */
@RestController
@PreAuthorize("hasAuthority('MARKETING_VIEW')")
@RequestMapping("/api/celebrations/events")
@RequiredArgsConstructor
public class MarketingEventController {

	static final Map<String, List<String>> SORTS = Map.of("start", List.of("startAt", "id"), "title",
			List.of("title", "id"), "updated", List.of("updatedAt", "id"));

	private final MarketingEventService eventService;

	@GetMapping
	public PageResponse<MarketingEventDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<MarketingEventType> type,
			@RequestParam(required = false) Set<MarketingEventStatus> status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long ownerId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return eventService.search(new EventFilter(search, type, status, from, to, ownerId),
				PageRequests.of(page, size, sort, SORTS, "start,asc"), viewer);
	}

	@GetMapping("/{id}")
	public MarketingEventDto get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return eventService.get(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public ResponseEntity<MarketingEventDto> create(@Valid @RequestBody SaveMarketingEvent request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public MarketingEventDto update(@PathVariable Long id, @Valid @RequestBody SaveMarketingEvent request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return eventService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		eventService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/attachments")
	public List<Attachment> attachments(@PathVariable Long id) {
		return eventService.attachments(id);
	}

	@PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public List<Attachment> addAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return eventService.addAttachment(id, file, actor, ClientInfo.from(http));
	}

	/** Always served as a download. */
	@GetMapping("/{id}/attachments/{fileId}")
	public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId) {
		return CelebrationDownloads.file(eventService.download(id, fileId));
	}

	@DeleteMapping("/{id}/attachments/{fileId}")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public List<Attachment> deleteAttachment(@PathVariable Long id, @PathVariable Long fileId,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return eventService.deleteAttachment(id, fileId, actor, ClientInfo.from(http));
	}

}
