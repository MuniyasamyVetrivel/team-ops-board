package com.teamops.admin.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.admin.dto.AuditLogDtos.AuditLogItem;
import com.teamops.admin.dto.AuditLogDtos.Catalog;
import com.teamops.admin.dto.AuditLogDtos.Filter;
import com.teamops.admin.service.AuditLogService;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditCatalog;
import com.teamops.common.report.ReportExporters;
import com.teamops.common.report.ReportFormat;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Audit log viewer (AUDIT_VIEW): every audited action, filtered by action, module, brief event (section 63), actor,
 * entity, business-day range and free text; newest first; CSV export.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
public class AuditLogController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("created", List.of("createdAt", "id"), "action",
			List.of("action", "createdAt"));

	private static final String DEFAULT_SORT = "created,desc";

	private final AuditLogService auditLogService;

	private final ReportExporters exporters;

	@GetMapping
	public PageResponse<AuditLogItem> search(@RequestParam(required = false) Set<AuditAction> action,
			@RequestParam(required = false) AuditCatalog.Module module,
			@RequestParam(required = false) AuditCatalog.BriefEvent event,
			@RequestParam(required = false) Long actorId, @RequestParam(required = false) String entityType,
			@RequestParam(required = false) Long entityId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size, @RequestParam(required = false) String sort) {
		Filter filter = new Filter(action, module, event, actorId, entityType, entityId, from, to, search);
		return auditLogService.search(filter, PageRequests.of(page, size, sort, SORT_FIELDS, DEFAULT_SORT));
	}

	/** Modules, actions, brief events, entity types and actors for the filters. */
	@GetMapping("/catalog")
	public Catalog catalog() {
		return auditLogService.catalog();
	}

	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) Set<AuditAction> action,
			@RequestParam(required = false) AuditCatalog.Module module,
			@RequestParam(required = false) AuditCatalog.BriefEvent event,
			@RequestParam(required = false) Long actorId, @RequestParam(required = false) String entityType,
			@RequestParam(required = false) Long entityId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String search, @RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "CSV") ReportFormat format) {
		Filter filter = new Filter(action, module, event, actorId, entityType, entityId, from, to, search);
		return exporters.download(
				auditLogService.export(filter, PageRequests.of(0, 1, sort, SORT_FIELDS, DEFAULT_SORT)), format);
	}

}
