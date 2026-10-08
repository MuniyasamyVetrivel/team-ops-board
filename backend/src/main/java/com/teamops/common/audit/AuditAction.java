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
	SLA_POLICY_UPDATED,
	PROJECT_CREATED,
	PROJECT_UPDATED,
	APPROVAL_SUBMITTED,
	APPROVAL_DECIDED,
	APPROVAL_CANCELLED,
	APPROVAL_WORKFLOW_UPDATED,
	ANNOUNCEMENT_PUBLISHED,
	ARTICLE_PUBLISHED,
	DOCUMENT_UPLOADED,
	DOCUMENT_DELETED,
	/** A validated CSV import was committed (type, file, imported and skipped counts). */
	CSV_IMPORTED,
	SEO_PAGE_CREATED,
	SEO_PAGE_UPDATED,
	SEO_PAGE_DELETED,
	SEO_KEYWORD_CREATED,
	SEO_KEYWORD_UPDATED,
	SEO_KEYWORD_DELETED,
	/** A month's position was recorded for a keyword (manual entry; CSV imports are covered by CSV_IMPORTED). */
	SEO_RANKING_RECORDED,
	/** An open month's position was corrected ({@code changes} holds from/to). */
	SEO_RANKING_CORRECTED,
	TARGET_TYPE_CREATED,
	TARGET_TYPE_UPDATED,
	TARGET_TYPE_DELETED,
	TARGET_CREATED,
	/** A month's target, actual, owner or notes changed ({@code changes} holds from/to). */
	TARGET_UPDATED,
	TARGET_DELETED,
	MARKETING_ACTIVITY_CREATED,
	MARKETING_ACTIVITY_UPDATED,
	MARKETING_ACTIVITY_DELETED,
	/** An occurrence without a task was completed, skipped or reopened by hand (task-backed ones follow the task). */
	MARKETING_ACTIVITY_OCCURRENCE_COMPLETED,
	MARKETING_ACTIVITY_OCCURRENCE_SKIPPED,
	MARKETING_ACTIVITY_OCCURRENCE_REOPENED,
	EMAIL_CAMPAIGN_CREATED,
	/** Details, status or counts changed ({@code changes} holds from/to). */
	EMAIL_CAMPAIGN_UPDATED,
	EMAIL_CAMPAIGN_DELETED

}
