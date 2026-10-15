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
	 * that are given); a module also matches the record changes of its record types. With {@code entityType} and
	 * {@code entityId} the entries of that record's children (comments, steps, attachments) match as well.
	 * {@code from}/{@code to} are business days, inclusive.
	 */
	public record Filter(Set<AuditAction> actions, AuditCatalog.Module module, AuditCatalog.BriefEvent event,
			Long actorId, String entityType, Long entityId, LocalDate from, LocalDate to, String search) {

		public Filter {
			actions = actions == null ? Set.of() : Set.copyOf(actions);
		}

	}

	/**
	 * One audit entry. {@code details} is the stored JSON document, parsed; for record changes it holds
	 * {@code changes: {field: {from, to}}} (user and department ids come with {@code fromLabel}/{@code toLabel}), and
	 * money amounts are hidden from viewers who cannot see finance. {@code actor} is null for system entries.
	 * {@code parentType}/{@code parentId} name the record this one belongs to (a comment's task). The module is the
	 * record's module for record changes. IP address and browser are only given to audit viewers.
	 */
	public record AuditLogItem(Long id, String action, String actionLabel, String module, String moduleLabel,
			UserSummary actor, String entityType, String entityTypeLabel, Long entityId, String entityLabel,
			String parentType, Long parentId, Object details, String ipAddress, String userAgent, Instant createdAt) {

	}

	public record ModuleInfo(String key, String label) {

	}

	public record ActionInfo(String action, String label, String module, List<String> events) {

	}

	public record EventInfo(String key, String label, List<String> actions) {

	}

	/** A record type that has automatic history (for the entity filter). */
	public record RecordTypeInfo(String type, String label, String module) {

	}

	/** Everything the viewer's filters offer. */
	public record Catalog(List<ModuleInfo> modules, List<ActionInfo> actions, List<EventInfo> events,
			List<String> entityTypes, List<RecordTypeInfo> recordTypes, List<UserSummary> actors) {

	}

}
