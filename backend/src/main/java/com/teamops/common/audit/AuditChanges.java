package com.teamops.common.audit;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Collects field changes for an audit entry as {@code {field: {from, to}}}; unchanged fields are skipped. */
public final class AuditChanges {

	private final Map<String, Object> changes = new LinkedHashMap<>();

	public AuditChanges track(String field, Object from, Object to) {
		if (!Objects.equals(from, to)) {
			Map<String, Object> change = new HashMap<>();
			change.put("from", from);
			change.put("to", to);
			changes.put(field, change);
		}
		return this;
	}

	public boolean isEmpty() {
		return changes.isEmpty();
	}

	/** Audit details payload: {@code {"changes": {...}}}. */
	public Map<String, Object> toDetails() {
		return Map.of("changes", changes);
	}

}
