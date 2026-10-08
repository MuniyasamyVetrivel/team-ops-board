package com.teamops.user.service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.teamops.common.exception.ApiException;

/**
 * Digital Marketing permissions only work together: the module is reachable only with MARKETING_VIEW, and an edit
 * permission is useless without the matching view permission. Rejecting inconsistent grants keeps an administrator
 * from giving someone SEO_EDIT and wondering why they cannot see the SEO pages.
 */
final class MarketingPermissionRules {

	static final String MARKETING_VIEW = "MARKETING_VIEW";

	/** Each marketing edit permission and the view permission it needs. */
	private static final List<String[]> EDIT_NEEDS_VIEW = List.of(new String[] { "MARKETING_EDIT", MARKETING_VIEW },
			new String[] { "SEO_EDIT", "SEO_VIEW" }, new String[] { "CAMPAIGN_EDIT", "CAMPAIGN_VIEW" },
			new String[] { "TARGET_EDIT", "TARGET_VIEW" }, new String[] { "LEAD_EDIT", "LEAD_VIEW" },
			new String[] { "BACKLINK_EDIT", "BACKLINK_VIEW" }, new String[] { "CONTENT_EDIT", "CONTENT_VIEW" });

	static final Set<String> MARKETING_PERMISSIONS = Set.of(MARKETING_VIEW, "MARKETING_EDIT", "SEO_VIEW", "SEO_EDIT",
			"CAMPAIGN_VIEW", "CAMPAIGN_EDIT", "TARGET_VIEW", "TARGET_EDIT", "LEAD_VIEW", "LEAD_EDIT", "BACKLINK_VIEW",
			"BACKLINK_EDIT", "CONTENT_VIEW", "CONTENT_EDIT");

	private MarketingPermissionRules() {
	}

	/** @param effective every permission the user would hold (roles and direct grants) */
	static void assertConsistent(Set<String> effective) {
		boolean anyMarketing = effective.stream().anyMatch(MARKETING_PERMISSIONS::contains);
		if (anyMarketing && !effective.contains(MARKETING_VIEW)) {
			throw ApiException.badRequest("MARKETING_VIEW_REQUIRED",
					"Digital Marketing permissions need \"View Digital Marketing\" as well");
		}
		Set<String> missing = new TreeSet<>();
		for (String[] pair : EDIT_NEEDS_VIEW) {
			if (effective.contains(pair[0]) && !effective.contains(pair[1])) {
				missing.add(pair[0] + " needs " + pair[1]);
			}
		}
		if (!missing.isEmpty()) {
			throw ApiException.badRequest("VIEW_PERMISSION_REQUIRED",
					"Edit permissions need their view permission: " + String.join(", ", missing));
		}
	}

}
