package com.teamops.common.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading audit details for a viewer. Finance amounts are logged in full but shown only to users who can see finance:
 * for everyone else a money field's change keeps its name and loses its values ({@code hidden: true}), and money
 * values inside business events are blanked and listed under {@code hiddenFields}.
 */
public final class AuditDetails {

	private AuditDetails() {
	}

	/** Whether an entry holds amounts only finance viewers may see. */
	public static boolean hasFinance(AuditAction action, Map<String, ?> details) {
		if (action != null && !AuditCatalog.financePaths(action).isEmpty()) {
			return true;
		}
		return details != null && details.get("changes") instanceof Map<?, ?> changes && changes.values()
			.stream()
			.anyMatch(change -> change instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("finance")));
	}

	/**
	 * The details as the viewer may see them: unchanged for finance viewers, otherwise a copy without amounts.
	 * {@code details} is the parsed JSON document (maps, lists and plain values).
	 */
	public static Object forViewer(Object details, AuditAction action, boolean canSeeFinance) {
		if (canSeeFinance || !(details instanceof Map<?, ?> map)) {
			return details;
		}
		Map<String, Object> copy = deepCopy(map);
		if (copy.get("changes") instanceof Map<?, ?> changes) {
			changes.values().forEach(change -> {
				if (change instanceof Map<?, ?> fields && Boolean.TRUE.equals(fields.get("finance"))) {
					hide(cast(fields));
				}
			});
		}
		List<String> hidden = new ArrayList<>();
		if (action != null) {
			for (String path : AuditCatalog.financePaths(action)) {
				if (blank(copy, path.split("\\."))) {
					hidden.add(path);
				}
			}
		}
		if (!hidden.isEmpty()) {
			copy.put("hiddenFields", hidden);
		}
		return copy;
	}

	private static boolean blank(Map<String, Object> root, String[] path) {
		Map<String, Object> node = root;
		for (int i = 0; i < path.length - 1; i++) {
			if (!(node.get(path[i]) instanceof Map<?, ?> next)) {
				return false;
			}
			node = cast(next);
		}
		String leaf = path[path.length - 1];
		if (!node.containsKey(leaf)) {
			return false;
		}
		if (node.get(leaf) instanceof Map<?, ?> change && (change.containsKey("from") || change.containsKey("to"))) {
			hide(cast(change));
		}
		else {
			node.put(leaf, null);
		}
		return true;
	}

	private static void hide(Map<String, Object> change) {
		change.remove("from");
		change.remove("to");
		change.put("hidden", true);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> cast(Map<?, ?> map) {
		return (Map<String, Object>) map;
	}

	private static Map<String, Object> deepCopy(Map<?, ?> source) {
		Map<String, Object> copy = new LinkedHashMap<>();
		source.forEach((key, value) -> copy.put(String.valueOf(key), copyValue(value)));
		return copy;
	}

	private static Object copyValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			return deepCopy(map);
		}
		if (value instanceof List<?> list) {
			return new ArrayList<>(list.stream().map(AuditDetails::copyValue).toList());
		}
		return value;
	}

}
