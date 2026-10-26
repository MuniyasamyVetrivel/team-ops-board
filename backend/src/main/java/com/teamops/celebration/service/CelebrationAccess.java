package com.teamops.celebration.service;

import com.teamops.common.security.AuthenticatedUser;

/**
 * Who may do what with celebrations, important days and events. They belong to Digital Marketing: only Super Admins and
 * MARKETING_VIEW holders see them (pages, calendar, "Coming up", reminders); among those, Super Admins and
 * CELEBRATIONS_EDIT holders create, edit and delete, and are the only ones who see full birth dates. Security config
 * and controllers check the permissions too; services use these rules to shape what they return.
 */
public final class CelebrationAccess {

	public static final String VIEW_PERMISSION = "MARKETING_VIEW";

	public static final String EDIT_PERMISSION = "CELEBRATIONS_EDIT";

	private CelebrationAccess() {
	}

	public static boolean canView(AuthenticatedUser user) {
		return user.isSuperAdmin() || user.hasPermission(VIEW_PERMISSION);
	}

	public static boolean canEdit(AuthenticatedUser user) {
		return user.isSuperAdmin() || (user.hasPermission(VIEW_PERMISSION) && user.hasPermission(EDIT_PERMISSION));
	}

	/** Birth year and full date of birth (privacy). */
	public static boolean canSeeBirthDates(AuthenticatedUser user) {
		return canEdit(user);
	}

}
