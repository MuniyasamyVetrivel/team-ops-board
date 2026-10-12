package com.teamops.team.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.team.dto.WorkHistoryDtos.DateField;
import com.teamops.team.dto.WorkHistoryDtos.TaskRow;
import com.teamops.team.dto.WorkHistoryDtos.TicketRow;
import com.teamops.team.dto.WorkHistoryDtos.WorkSummary;
import com.teamops.team.service.WorkHistoryService;
import com.teamops.team.service.WorkHistoryService.Export;
import com.teamops.team.service.WorkHistoryService.Filter;

import lombok.RequiredArgsConstructor;

/**
 * A team member's work history. Every endpoint takes the same filters: {@code status} (task or ticket statuses plus
 * OVERDUE, several allowed), {@code dateField} with {@code from}/{@code to} (inclusive business dates), project (tasks)
 * or category (tickets), {@code priority} (several allowed) and {@code search}. The service decides whose work the
 * viewer may see.
 */
@RestController
@RequestMapping("/api/team/{userId}")
@RequiredArgsConstructor
public class TeamWorkController {

	static final Map<String, List<String>> TASK_SORTS = Map.of("due", List.of("dueDate", "id"), "completed",
			List.of("completedAt", "id"), "priority", List.of("priority"), "updated", List.of("updatedAt", "id"));

	static final Map<String, List<String>> TICKET_SORTS = Map.of("due", List.of("resolutionDueAt", "id"), "completed",
			List.of("resolvedAt", "id"), "priority", List.of("priorityRank", "createdAt"), "updated",
			List.of("updatedAt", "id"));

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private static final String DEFAULT_SORT = "updated,desc";

	private final WorkHistoryService workHistoryService;

	@GetMapping("/tasks")
	@PreAuthorize("hasAuthority('TEAM_VIEW') and hasAuthority('TASK_VIEW')")
	public PageResponse<TaskRow> tasks(@PathVariable Long userId, @RequestParam(required = false) Set<String> status,
			@RequestParam(required = false) DateField dateField,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Set<String> priority,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return workHistoryService.tasks(userId, new Filter(status, dateField, from, to, projectId, priority, search),
				PageRequests.of(page, size, sort, TASK_SORTS, DEFAULT_SORT), viewer);
	}

	/** Counts per status, completed on time %, average days to complete, overdue, actual hours, completed per week. */
	@GetMapping("/tasks/summary")
	@PreAuthorize("hasAuthority('TEAM_VIEW') and hasAuthority('TASK_VIEW')")
	public WorkSummary taskSummary(@PathVariable Long userId, @RequestParam(required = false) Set<String> status,
			@RequestParam(required = false) DateField dateField,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Set<String> priority,
			@RequestParam(required = false) String search, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return workHistoryService.taskSummary(userId, new Filter(status, dateField, from, to, projectId, priority, search),
				viewer);
	}

	/** The filtered list as CSV, in the list's order. */
	@GetMapping("/tasks/export")
	@PreAuthorize("hasAuthority('TEAM_VIEW') and hasAuthority('TASK_VIEW')")
	public ResponseEntity<byte[]> exportTasks(@PathVariable Long userId, @RequestParam(required = false) Set<String> status,
			@RequestParam(required = false) DateField dateField,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Set<String> priority,
			@RequestParam(required = false) String search, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return csv(workHistoryService.exportTasks(userId, new Filter(status, dateField, from, to, projectId, priority, search),
				PageRequests.of(0, 1, sort, TASK_SORTS, DEFAULT_SORT).getSort(), viewer));
	}

	@GetMapping("/tickets")
	@PreAuthorize("hasAuthority('TEAM_VIEW') and hasAuthority('TICKET_VIEW')")
	public PageResponse<TicketRow> tickets(@PathVariable Long userId, @RequestParam(required = false) Set<String> status,
			@RequestParam(required = false) DateField dateField,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Set<String> priority,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return workHistoryService.tickets(userId, new Filter(status, dateField, from, to, categoryId, priority, search),
				PageRequests.of(page, size, sort, TICKET_SORTS, DEFAULT_SORT), viewer);
	}

	@GetMapping("/tickets/summary")
	@PreAuthorize("hasAuthority('TEAM_VIEW') and hasAuthority('TICKET_VIEW')")
	public WorkSummary ticketSummary(@PathVariable Long userId, @RequestParam(required = false) Set<String> status,
			@RequestParam(required = false) DateField dateField,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Set<String> priority,
			@RequestParam(required = false) String search, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return workHistoryService.ticketSummary(userId,
				new Filter(status, dateField, from, to, categoryId, priority, search), viewer);
	}

	@GetMapping("/tickets/export")
	@PreAuthorize("hasAuthority('TEAM_VIEW') and hasAuthority('TICKET_VIEW')")
	public ResponseEntity<byte[]> exportTickets(@PathVariable Long userId,
			@RequestParam(required = false) Set<String> status, @RequestParam(required = false) DateField dateField,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Set<String> priority,
			@RequestParam(required = false) String search, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return csv(workHistoryService.exportTickets(userId,
				new Filter(status, dateField, from, to, categoryId, priority, search),
				PageRequests.of(0, 1, sort, TICKET_SORTS, DEFAULT_SORT).getSort(), viewer));
	}

	private static ResponseEntity<byte[]> csv(Export export) {
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(export.fileName()).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(export.content());
	}

}
