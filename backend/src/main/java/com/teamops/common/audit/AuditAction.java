package com.teamops.common.audit;

/** Audited event types. Extend as modules are added (task, ticket, target, ranking, campaign, ...). */
public enum AuditAction {

	LOGIN,
	LOGIN_FAILED,
	LOGOUT,
	REFRESH_TOKEN_REUSE,
	USER_CREATED,
	USER_UPDATED,
	USER_DISABLED,
	USER_ENABLED,
	USER_PASSWORD_RESET,
	/** Roles or direct permission grants changed (brief section 63: "User permission change"). */
	USER_ACCESS_CHANGED,
	DEPARTMENT_CREATED,
	DEPARTMENT_UPDATED,
	DEPARTMENT_MEMBER_ADDED,
	DEPARTMENT_MEMBER_REMOVED,
	TASK_CREATED,
	TASK_ASSIGNED,
	TASK_STATUS_CHANGED,
	TICKET_CREATED,
	TICKET_ASSIGNED,
	TICKET_STATUS_CHANGED,
	SLA_POLICY_UPDATED

}
