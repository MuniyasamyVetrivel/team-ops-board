package com.teamops.history.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.admin.dto.AuditLogDtos.AuditLogItem;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.history.service.RecordHistoryService;

import lombok.RequiredArgsConstructor;

/**
 * A record's History tab, e.g. {@code GET /api/history/TASK/42}: its audit entries and those of its comments,
 * checklist, attachments and other child records, newest first. Needs HISTORY_VIEW (Super Admin only by default;
 * 403 otherwise), and then the record must be one the viewer may open (404 otherwise). Read-only.
 */
@RestController
@PreAuthorize("hasAuthority('HISTORY_VIEW')")
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class RecordHistoryController {

	private static final Map<String, List<String>> SORT_FIELDS = Map.of("created", List.of("createdAt", "id"));

	private final RecordHistoryService historyService;

	@GetMapping("/{entityType}/{entityId}")
	public PageResponse<AuditLogItem> history(@PathVariable String entityType, @PathVariable Long entityId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return historyService.history(entityType, entityId,
				PageRequests.of(page, size, null, SORT_FIELDS, "created,desc"), viewer);
	}

}
