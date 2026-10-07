package com.teamops.common.audit;

/** Audited event types. Extend as modules are added (task, ticket, target, ranking, campaign, ...). */
public enum AuditAction {

	LOGIN,
	LOGIN_FAILED,
	LOGOUT,
	REFRESH_TOKEN_REUSE,
	USER_CREATED

}
