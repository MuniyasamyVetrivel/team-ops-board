package com.teamops.common.security;

import com.teamops.user.entity.PermissionCodes;

/** Who may see finance amounts (budgets, spend, approval amounts), wherever they appear. */
public final class FinanceAccess {

	private FinanceAccess() {
	}

	public static boolean canView(AuthenticatedUser user) {
		return user != null && (user.isSuperAdmin() || user.hasPermission(PermissionCodes.FINANCE_VIEW));
	}

}
