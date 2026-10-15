package com.teamops.history.service;

import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.teamops.admin.dto.AuditLogDtos.AuditLogItem;
import com.teamops.admin.service.AuditLogService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/** The History tab of a record: who changed what and when, for the record and the records that belong to it. */
@Service
@RequiredArgsConstructor
public class RecordHistoryService {

	private final RecordHistoryAccess access;

	private final AuditLogService auditLogService;

	public PageResponse<AuditLogItem> history(String entityType, Long entityId, Pageable pageable,
			AuthenticatedUser viewer) {
		String type = entityType.trim().toUpperCase(Locale.ROOT);
		access.check(type, entityId, viewer);
		return auditLogService.history(type, entityId, pageable, viewer);
	}

}
