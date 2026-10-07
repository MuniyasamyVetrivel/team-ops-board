package com.teamops.calendar.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.teamops.calendar.dto.CalendarDtos.CalendarResponse;
import com.teamops.calendar.dto.CalendarDtos.EventDetail;
import com.teamops.calendar.dto.CalendarDtos.SaveEvent;
import com.teamops.calendar.service.CalendarService;
import com.teamops.common.security.AuthenticatedUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Calendar API: merged events and task deadlines, plus event management. The service applies scope. */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CALENDAR_VIEW')")
public class CalendarController {

	private final CalendarService calendarService;

	@GetMapping
	public CalendarResponse range(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "false") boolean mine, @AuthenticationPrincipal AuthenticatedUser actor) {
		return calendarService.range(from, to, mine, actor);
	}

	@GetMapping("/events/{id}")
	public EventDetail get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		return calendarService.get(id, actor);
	}

	@PostMapping("/events")
	@PreAuthorize("hasAuthority('CALENDAR_EDIT')")
	public ResponseEntity<EventDetail> create(@Valid @RequestBody SaveEvent request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return ResponseEntity.status(HttpStatus.CREATED).body(calendarService.create(request, actor));
	}

	@PutMapping("/events/{id}")
	@PreAuthorize("hasAuthority('CALENDAR_EDIT')")
	public EventDetail update(@PathVariable Long id, @Valid @RequestBody SaveEvent request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return calendarService.update(id, request, actor);
	}

	@DeleteMapping("/events/{id}")
	@PreAuthorize("hasAuthority('CALENDAR_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		calendarService.delete(id, actor);
		return ResponseEntity.noContent().build();
	}

}
