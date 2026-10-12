package com.teamops.celebration.service;

import com.teamops.common.security.AuthenticatedUser;

/**
 * Who may do what with celebrations, important days and events. Every signed-in user sees them (names, day and month);
 * Super Admins and CELEBRATIONS_EDIT holders create, edit and delete, and are the only ones who see full birth dates.
 * Controllers check the permission too; services use these rules to shape what they return.
 */
public final class CelebrationAccess {

	public static final String EDIT_PERMISSION = "CELEBRATIONS_EDIT";

	private CelebrationAccess() {
	}

	public static boolean canEdit(AuthenticatedUser user) {
		return user.isSuperAdmin() || user.hasPermission(EDIT_PERMISSION);
	}

	/** Birth year and full date of birth (privacy). */
	public static boolean canSeeBirthDates(AuthenticatedUser user) {
		return canEdit(user);
	}

}
