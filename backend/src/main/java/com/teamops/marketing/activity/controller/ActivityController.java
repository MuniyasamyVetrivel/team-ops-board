package com.teamops.marketing.activity.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.activity.dto.ActivityDtos.ActivityDetail;
import com.teamops.marketing.activity.dto.ActivityDtos.ActivityListItem;
import com.teamops.marketing.activity.dto.ActivityDtos.CreateActivity;
import com.teamops.marketing.activity.dto.ActivityDtos.OccurrenceAction;
import com.teamops.marketing.activity.dto.ActivityDtos.OccurrenceItem;
import com.teamops.marketing.activity.dto.ActivityDtos.UpdateActivity;
import com.teamops.marketing.activity.entity.Frequency;
import com.teamops.marketing.activity.entity.OccurrenceStatus;
import com.teamops.marketing.activity.service.ActivityService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Recurring marketing activities and their occurrences. Every marketing user (MARKETING_VIEW) can see them; marketing
 * managers (MARKETING_EDIT) configure them. Occurrences with a task follow the task; the others are completed, skipped
 * or reopened here by the activity's owner or assignee, or a marketing manager.
 */
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class ActivityController {

	static final Map<String, List<String>> ACTIVITY_SORTS = Map.of("name", List.of("name", "id"), "frequency",
			List.of("frequency", "name"), "start", List.of("startDate", "name"), "updated", List.of("updatedAt"));

	static final Map<String, List<String>> OCCURRENCE_SORTS = Map.of("due", List.of("dueDate", "id"), "period",
			List.of("periodStart", "id"));

	private final ActivityService activityService;

	@GetMapping("/activities")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public PageResponse<ActivityListItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Frequency frequency, @RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Long ownerId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return activityService.search(search, frequency, active, ownerId,
				PageRequests.of(page, size, sort, ACTIVITY_SORTS, "name,asc"), viewer);
	}

	@GetMapping("/activities/{id}")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public ActivityDetail get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return activityService.get(id, viewer);
	}

	/** The activity's whole history, newest period first. */
	@GetMapping("/activities/{id}/occurrences")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public PageResponse<OccurrenceItem> history(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return activityService.occurrencesOf(id, PageRequests.of(page, size, null, OCCURRENCE_SORTS, "period,desc"),
				viewer);
	}

	@PostMapping("/activities")
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public ResponseEntity<ActivityDetail> create(@Valid @RequestBody CreateActivity request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(activityService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/activities/{id}")
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public ActivityDetail update(@PathVariable Long id, @Valid @RequestBody UpdateActivity request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return activityService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/activities/{id}")
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		activityService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	/** Occurrences across activities, e.g. everything open and due by a date, earliest due first. */
	@GetMapping("/activity-occurrences")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public PageResponse<OccurrenceItem> occurrences(@RequestParam(required = false) Set<OccurrenceStatus> status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
			@RequestParam(required = false) Long assigneeId, @RequestParam(required = false) Long activityId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return activityService.occurrences(status, dueFrom, dueTo, assigneeId, activityId,
				PageRequests.of(page, size, sort, OCCURRENCE_SORTS, "due,asc"), viewer);
	}

	@PostMapping("/activity-occurrences/{id}/complete")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public OccurrenceItem complete(@PathVariable Long id, @Valid @RequestBody(required = false) OccurrenceAction request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return activityService.complete(id, request == null ? null : request.notes(), actor, ClientInfo.from(http));
	}

	@PostMapping("/activity-occurrences/{id}/skip")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public OccurrenceItem skip(@PathVariable Long id, @Valid @RequestBody(required = false) OccurrenceAction request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return activityService.skip(id, request == null ? null : request.notes(), actor, ClientInfo.from(http));
	}

	@PostMapping("/activity-occurrences/{id}/reopen")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public OccurrenceItem reopen(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return activityService.reopen(id, actor, ClientInfo.from(http));
	}

}
