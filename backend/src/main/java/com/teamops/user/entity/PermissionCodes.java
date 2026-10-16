package com.teamops.user.entity;

/** Permission codes referenced from Java code. The full catalogue lives in V2__reference_data.sql. */
public final class PermissionCodes {

	public static final String USER_MANAGE = "USER_MANAGE";

	public static final String PERMISSION_MANAGE = "PERMISSION_MANAGE";

	public static final String DEPARTMENT_MANAGE = "DEPARTMENT_MANAGE";

	public static final String TEAM_VIEW = "TEAM_VIEW";

	public static final String AUDIT_VIEW = "AUDIT_VIEW";

	/** The History tab on records (Super Admin only by default). */
	public static final String HISTORY_VIEW = "HISTORY_VIEW";

	/** The Recycle Bin: view, restore and permanent delete (Super Admin only by default). */
	public static final String RECYCLE_BIN_MANAGE = "RECYCLE_BIN_MANAGE";

	/** See money amounts (budgets, spend, approval amounts), including in audit history. */
	public static final String FINANCE_VIEW = "FINANCE_VIEW";

	private PermissionCodes() {
	}

}
