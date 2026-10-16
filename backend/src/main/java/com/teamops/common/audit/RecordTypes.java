package com.teamops.common.audit;

import java.util.Locale;
import java.util.Map;

import com.teamops.common.audit.AuditCatalog.Module;

/**
 * Names and modules of audited record types. A type is the entity class name in upper snake case ({@code Task} →
 * {@code TASK}, {@code MarketingLead} → {@code MARKETING_LEAD}) unless the class says otherwise with
 * {@link AuditType}; its module follows from the entity's package.
 */
public final class RecordTypes {

	private static final String ROOT = "com.teamops.";

	/** Package (below {@code com.teamops}) → module; the longest matching prefix wins. */
	static final Map<String, Module> MODULE_BY_PACKAGE = Map.ofEntries(Map.entry("user", Module.USERS),
			Map.entry("auth", Module.SIGN_IN), Map.entry("department", Module.DEPARTMENTS),
			Map.entry("task", Module.TASKS), Map.entry("tag", Module.TASKS), Map.entry("ticket", Module.TICKETS),
			Map.entry("sla", Module.TICKETS), Map.entry("project", Module.PROJECTS),
			Map.entry("approval", Module.APPROVALS), Map.entry("announcement", Module.COLLABORATION),
			Map.entry("knowledge", Module.COLLABORATION), Map.entry("document", Module.COLLABORATION),
			Map.entry("calendar", Module.COLLABORATION), Map.entry("notification", Module.COLLABORATION),
			Map.entry("common.storage", Module.COLLABORATION), Map.entry("common.settings", Module.SETTINGS),
			Map.entry("marketing.seo", Module.SEO), Map.entry("marketing.target", Module.TARGETS),
			Map.entry("marketing.activity", Module.ACTIVITIES), Map.entry("marketing.email", Module.CAMPAIGNS),
			Map.entry("marketing.paid", Module.CAMPAIGNS), Map.entry("marketing.lead", Module.LEADS),
			Map.entry("marketing.backlink", Module.BACKLINKS), Map.entry("marketing.content", Module.CONTENT),
			Map.entry("marketing.report", Module.REPORTS), Map.entry("celebration", Module.CELEBRATIONS),
			Map.entry("module", Module.MODULES), Map.entry("bench", Module.BENCH),
			Map.entry("vendor", Module.VENDORS));

	private RecordTypes() {
	}

	public static String typeOf(Class<?> entityClass) {
		AuditType explicit = entityClass.getAnnotation(AuditType.class);
		if (explicit != null) {
			return explicit.value();
		}
		return entityClass.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
	}

	/** The module an entity's records belong to, or null when its package is not mapped. */
	public static Module moduleOf(Class<?> entityClass) {
		String name = entityClass.getPackageName();
		if (!name.startsWith(ROOT)) {
			return null;
		}
		String relative = name.substring(ROOT.length());
		Module best = null;
		int bestLength = -1;
		for (Map.Entry<String, Module> entry : MODULE_BY_PACKAGE.entrySet()) {
			String prefix = entry.getKey();
			boolean matches = relative.equals(prefix) || relative.startsWith(prefix + ".");
			if (matches && prefix.length() > bestLength) {
				best = entry.getValue();
				bestLength = prefix.length();
			}
		}
		return best;
	}

	/** "MARKETING_LEAD" → "Marketing lead". */
	public static String labelOf(String type) {
		if (type == null || type.isBlank()) {
			return type;
		}
		String words = type.replace('_', ' ').toLowerCase(Locale.ROOT);
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	public static boolean isAudited(Class<?> entityClass) {
		return !entityClass.isAnnotationPresent(AuditIgnore.class);
	}

}
