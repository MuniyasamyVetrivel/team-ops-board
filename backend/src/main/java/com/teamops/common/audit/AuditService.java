package com.teamops.common.audit;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.web.ClientInfo;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes audit records in their own transaction so that events such as failed logins are kept even when the
 * surrounding business transaction fails.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

	private final AuditLogRepository auditLogRepository;

	private final ObjectMapper objectMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(AuditAction action, Long actorId, String entityType, Long entityId, Map<String, ?> details,
			ClientInfo client) {
		AuditLog log = new AuditLog();
		log.setAction(action.name());
		log.setActorId(actorId);
		log.setEntityType(entityType);
		log.setEntityId(entityId);
		log.setDetails(details == null || details.isEmpty() ? null : objectMapper.writeValueAsString(details));
		if (client != null) {
			log.setIpAddress(client.ipAddress());
			log.setUserAgent(client.userAgent());
		}
		auditLogRepository.save(log);
	}

}
