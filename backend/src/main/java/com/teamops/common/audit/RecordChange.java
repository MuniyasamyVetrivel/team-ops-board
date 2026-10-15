package com.teamops.common.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A pending audit entry for one record, collected during a transaction and written just before it commits. Field
 * changes are kept as {@code {field: {from, to, ...flags}}}, the shape {@link AuditChanges} uses. Flags: {@code ref}
 * (the values are ids of that record type), {@code finance} (money: shown only to finance viewers) and
 * {@code redacted} (a secret: no values are stored).
 */
final class RecordChange {

	final AuditAction action;

	final String entityType;

	final Long entityId;

	/** Composite keys (attachment rows) as field → value; null for ordinary ids. */
	final Map<String, Object> key;

	String label;

	AuditRef parent;

	final Long actorId;

	final String ipAddress;

	final String userAgent;

	final Map<String, Map<String, Object>> changes = new LinkedHashMap<>();

	RecordChange(AuditAction action, String entityType, Long entityId, Map<String, Object> key, Long actorId,
			String ipAddress, String userAgent) {
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.key = key;
		this.actorId = actorId;
		this.ipAddress = ipAddress;
		this.userAgent = userAgent;
	}

	void change(String field, Object from, Object to, String ref, boolean finance) {
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("from", from);
		change.put("to", to);
		if (ref != null) {
			change.put("ref", ref);
		}
		if (finance) {
			change.put("finance", true);
		}
		changes.put(field, change);
	}

	/** A secret changed: the field is named, its values are not. */
	void redacted(String field) {
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("redacted", true);
		changes.put(field, change);
	}

	/** Folds a later change of the same field into this entry: the first "from" stays, the last "to" wins. */
	void merge(String field, Map<String, Object> later) {
		Map<String, Object> earlier = changes.get(field);
		if (earlier == null) {
			changes.put(field, new LinkedHashMap<>(later));
			return;
		}
		later.forEach((name, value) -> {
			if (!"from".equals(name)) {
				earlier.put(name, value);
			}
		});
	}

	/** Drops fields that ended where they started; a redacted field always stays. */
	void dropUnchanged() {
		changes.values()
			.removeIf(change -> !Boolean.TRUE.equals(change.get("redacted"))
					&& AuditValues.same(change.get("from"), change.get("to")));
	}

	boolean finance() {
		return changes.values().stream().anyMatch(change -> Boolean.TRUE.equals(change.get("finance")));
	}

	Map<String, Object> details() {
		Map<String, Object> details = new LinkedHashMap<>();
		if (key != null) {
			details.put("key", key);
		}
		details.put("changes", changes);
		return details;
	}

	String identity() {
		return entityType + "#" + (entityId != null ? entityId : key);
	}

}
