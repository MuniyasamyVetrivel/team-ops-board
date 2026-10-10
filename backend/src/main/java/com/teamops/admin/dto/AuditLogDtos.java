package com.teamops.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditCatalog;
import com.teamops.user.dto.UserSummary;

public final class AuditLogDtos {

	private AuditLogDtos() {
	}

	/**
	 * Audit log filters. {@code actions}, {@code module} and {@code event} narrow each other (an entry must match all
	 * that are given); {@code from}/{@code to} are business days, inclusive.
	 */
	public record Filter(Set<AuditAction> actions, AuditCatalog.Module module, AuditCatalog.BriefEvent event,
			Long actorId, String entityType, Long entityId, LocalDate from, LocalDate to, String search) {

		public Filter {
			actions = actions == null ? Set.of() : Set.copyOf(actions);
		}

	}

	/** {@code details} is the stored JSON document, parsed. {@code actor} is null for system entries. */
	public record AuditLogItem(Long id, String action, String actionLabel, String module, String moduleLabel,
			UserSummary actor, String entityType, Long entityId, Object details, String ipAddress, String userAgent,
			Instant createdAt) {

	}

	public record ModuleInfo(String key, String label) {

	}

	public record ActionInfo(String action, String label, String module, List<String> events) {

	}

	public record EventInfo(String key, String label, List<String> actions) {

	}

	/** Everything the viewer's filters offer. */
	public record Catalog(List<ModuleInfo> modules, List<ActionInfo> actions, List<EventInfo> events,
			List<String> entityTypes, List<UserSummary> actors) {

	}

}
