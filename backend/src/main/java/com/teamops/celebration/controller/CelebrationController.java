package com.teamops.celebration.controller;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.calendar.dto.CalendarDtos.CalendarResponse;
import com.teamops.celebration.dto.CelebrationDtos.LinkedInPostDto;
import com.teamops.celebration.dto.CelebrationDtos.LinkedInPostRow;
import com.teamops.celebration.dto.CelebrationDtos.SaveLinkedInPost;
import com.teamops.celebration.entity.LinkedInPostStatus;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.service.LinkedInPostService;
import com.teamops.celebration.service.OccasionCalendar;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * "Coming up" (the dashboard widget) and the LinkedIn posts list: every signed-in user reads; CELEBRATIONS_EDIT
 * changes post tracking.
 */
@RestController
@RequestMapping("/api/celebrations")
@RequiredArgsConstructor
public class CelebrationController {

	private final OccasionCalendar occasionCalendar;

	private final LinkedInPostService linkedInPostService;

	/** Birthdays, work anniversaries, important days and events from today through the next {@code days} (14) days. */
	@GetMapping("/upcoming")
	public CalendarResponse upcoming(@RequestParam(required = false) Integer days) {
		return occasionCalendar.upcoming(days);
	}

	/** Everything coming up in [from, to] (today and the next 60 days by default) with its LinkedIn post status. */
	@GetMapping("/linkedin-posts")
	public PageResponse<LinkedInPostRow> linkedInPosts(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Set<LinkedInPostStatus> status,
			@RequestParam(required = false) Boolean needed, @RequestParam(required = false) Set<OccasionKind> kind,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return linkedInPostService.list(from, to, status, needed, kind,
				PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));
	}

	@PutMapping("/linkedin-posts")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public LinkedInPostDto saveLinkedInPost(@Valid @RequestBody SaveLinkedInPost request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return linkedInPostService.save(request, actor, ClientInfo.from(http));
	}

}
