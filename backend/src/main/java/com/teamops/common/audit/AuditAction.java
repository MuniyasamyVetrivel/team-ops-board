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
	APPROVAL_TYPE_CREATED,
	/** Name, description, amount rule or active flag changed ({@code changes} holds from/to). */
	APPROVAL_TYPE_UPDATED,
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
	EMAIL_CAMPAIGN_DELETED,
	PAID_CAMPAIGN_CREATED,
	/** The plan (dates, budget, status, owner) changed ({@code changes} holds from/to). */
	PAID_CAMPAIGN_UPDATED,
	PAID_CAMPAIGN_DELETED,
	/** A month's results were added (by hand or CSV). */
	PAID_RESULTS_RECORDED,
	/** A recorded month was corrected; {@code closedMonth} marks a Super Admin correction of a closed month. */
	PAID_RESULTS_CORRECTED,
	/** By hand or CSV ({@code source} in the details). */
	LEAD_CREATED,
	/** Details, source, date or link changed ({@code changes} holds from/to); {@code closedMonth} for a Super Admin. */
	LEAD_UPDATED,
	LEAD_STATUS_CHANGED,
	LEAD_DELETED,
	/** By hand or CSV ({@code origin} in the details). */
	BACKLINK_CREATED,
	/** Details or stage dates changed ({@code changes} holds from/to); {@code closedMonth} for a Super Admin. */
	BACKLINK_UPDATED,
	/** Moved to another status, with the stage dates it set or cleared. */
	BACKLINK_STATUS_CHANGED,
	BACKLINK_DELETED,
	CONTENT_CREATED,
	/** Details, status or dates changed ({@code changes} holds from/to); {@code closedMonth} for a Super Admin. */
	CONTENT_UPDATED,
	/** Moved to another status, with the publication or refresh date it set or cleared. */
	CONTENT_STATUS_CHANGED,
	CONTENT_DELETED,
	/** An ended month's Digital Marketing report was frozen (kept as it stood). */
	MARKETING_REPORT_FROZEN,
	/** An admin setting changed ({@code changes} holds from/to). */
	SETTING_UPDATED,
	/** A role's permissions changed ({@code added}, {@code removed}); brief section 63: "User permission change". */
	ROLE_PERMISSIONS_CHANGED

}
