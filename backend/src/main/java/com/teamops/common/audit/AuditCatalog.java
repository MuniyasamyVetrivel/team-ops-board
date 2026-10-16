package com.teamops.common.audit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Groups every {@link AuditAction} into a module with a readable label, and maps the events the brief requires the
 * audit log to track (section 63) to the actions that record them. The switch is exhaustive, so a new action does not
 * compile until it is placed in a module.
 */
public final class AuditCatalog {

	public enum Module {

		SIGN_IN("Sign-in"), USERS("Users and access"), DEPARTMENTS("Departments"), TASKS("Tasks"),
		TICKETS("Help desk"), PROJECTS("Projects"), APPROVALS("Approvals"), COLLABORATION("Collaboration"),
		IMPORTS("CSV imports"), SEO("SEO"), TARGETS("Marketing targets"), ACTIVITIES("Marketing activities"),
		CAMPAIGNS("Campaigns"), LEADS("Leads"), BACKLINKS("Backlinks"), CONTENT("Content"),
		CELEBRATIONS("Celebrations and events"), REPORTS("Reports"), SETTINGS("Settings"),
		BACKUPS("Backups and exports"), MODULES("Department tools"), BENCH("Bench management"),
		/** The automatic record changes of every module (each entry also belongs to its record's module). */
		RECORDS("Record changes");

		private final String label;

		Module(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

	}

	/** The audit events listed in the brief (section 63), each recorded by one or more actions. */
	public enum BriefEvent {

		USER_LOGIN("User login", Set.of(AuditAction.LOGIN)),
		TASK_CREATION("Task creation", Set.of(AuditAction.TASK_CREATED)),
		TASK_ASSIGNMENT("Task assignment", Set.of(AuditAction.TASK_ASSIGNED)),
		TASK_STATUS_CHANGE("Task status change", Set.of(AuditAction.TASK_STATUS_CHANGED)),
		TICKET_CREATION("Ticket creation", Set.of(AuditAction.TICKET_CREATED)),
		TICKET_STATUS_CHANGE("Ticket status change", Set.of(AuditAction.TICKET_STATUS_CHANGED)),
		TARGET_MODIFICATION("Target modification",
				Set.of(AuditAction.TARGET_CREATED, AuditAction.TARGET_UPDATED, AuditAction.TARGET_DELETED,
						AuditAction.TARGET_TYPE_CREATED, AuditAction.TARGET_TYPE_UPDATED,
						AuditAction.TARGET_TYPE_DELETED)),
		SEO_RANKING_MODIFICATION("SEO ranking modification",
				Set.of(AuditAction.SEO_RANKING_RECORDED, AuditAction.SEO_RANKING_CORRECTED)),
		CAMPAIGN_CREATION("Campaign creation",
				Set.of(AuditAction.EMAIL_CAMPAIGN_CREATED, AuditAction.PAID_CAMPAIGN_CREATED)),
		CAMPAIGN_MODIFICATION("Campaign modification",
				Set.of(AuditAction.EMAIL_CAMPAIGN_UPDATED, AuditAction.EMAIL_CAMPAIGN_DELETED,
						AuditAction.PAID_CAMPAIGN_UPDATED, AuditAction.PAID_CAMPAIGN_DELETED,
						AuditAction.PAID_RESULTS_RECORDED, AuditAction.PAID_RESULTS_CORRECTED)),
		LEAD_MODIFICATION("Lead modification",
				Set.of(AuditAction.LEAD_CREATED, AuditAction.LEAD_UPDATED, AuditAction.LEAD_STATUS_CHANGED,
						AuditAction.LEAD_DELETED)),
		BACKLINK_MODIFICATION("Backlink modification",
				Set.of(AuditAction.BACKLINK_CREATED, AuditAction.BACKLINK_UPDATED, AuditAction.BACKLINK_STATUS_CHANGED,
						AuditAction.BACKLINK_DELETED)),
		USER_PERMISSION_CHANGE("User permission change",
				Set.of(AuditAction.USER_ACCESS_CHANGED, AuditAction.ROLE_PERMISSIONS_CHANGED));

		private final String label;

		private final Set<AuditAction> actions;

		BriefEvent(String label, Set<AuditAction> actions) {
			this.label = label;
			this.actions = actions;
		}

		public String label() {
			return label;
		}

		public Set<AuditAction> actions() {
			return actions;
		}

	}

	private AuditCatalog() {
	}

	/** Created, updated, deleted, restored and permanently deleted records, written for every entity. */
	public static final Set<AuditAction> RECORD_ACTIONS = Set.of(AuditAction.RECORD_CREATED,
			AuditAction.RECORD_UPDATED, AuditAction.RECORD_DELETED, AuditAction.RECORD_RESTORED,
			AuditAction.RECORD_PURGED);

	/**
	 * Money amounts inside the details of business events, as dotted paths ("changes.budget" is a whole from/to pair).
	 * Only users who can see finance see them; {@link AuditDetails#mask} hides them for everyone else.
	 */
	private static final Map<AuditAction, List<String>> FINANCE_PATHS = Map.of(AuditAction.APPROVAL_SUBMITTED,
			List.of("amount"), AuditAction.PAID_CAMPAIGN_CREATED, List.of("budget"), AuditAction.PAID_CAMPAIGN_UPDATED,
			List.of("changes.budget"), AuditAction.PAID_RESULTS_RECORDED, List.of("results.spend"),
			AuditAction.PAID_RESULTS_CORRECTED, List.of("changes.results.from.spend", "changes.results.to.spend"));

	public static List<String> financePaths(AuditAction action) {
		return FINANCE_PATHS.getOrDefault(action, List.of());
	}

	public static boolean isRecordChange(AuditAction action) {
		return RECORD_ACTIONS.contains(action);
	}

	/**
	 * The module an action is filed under. Record changes are filed under {@link Module#RECORDS} here; an entry's own
	 * module comes from its entity type ({@link RecordTypes}).
	 */
	public static Module moduleOf(AuditAction action) {
		return switch (action) {
			case RECORD_CREATED, RECORD_UPDATED, RECORD_DELETED, RECORD_RESTORED, RECORD_PURGED -> Module.RECORDS;
			case LOGIN, LOGIN_FAILED, LOGOUT, REFRESH_TOKEN_REUSE, PASSWORD_CHANGED -> Module.SIGN_IN;
			case USER_CREATED, USER_UPDATED, USER_PHOTO_CHANGED, USER_DISABLED, USER_ENABLED, USER_PASSWORD_RESET,
					USER_ACCESS_CHANGED, ROLE_PERMISSIONS_CHANGED, USERS_IMPORTED, BREAK_GLASS_USED ->
				Module.USERS;
			case DEPARTMENT_CREATED, DEPARTMENT_UPDATED, DEPARTMENT_MEMBER_ADDED, DEPARTMENT_MEMBER_REMOVED ->
				Module.DEPARTMENTS;
			case TASK_CREATED, TASK_ASSIGNED, TASK_STATUS_CHANGED, TASK_DELETED -> Module.TASKS;
			case TICKET_CREATED, TICKET_ASSIGNED, TICKET_STATUS_CHANGED, SLA_POLICY_UPDATED -> Module.TICKETS;
			case PROJECT_CREATED, PROJECT_UPDATED -> Module.PROJECTS;
			case APPROVAL_SUBMITTED, APPROVAL_DECIDED, APPROVAL_CANCELLED, APPROVAL_WORKFLOW_UPDATED,
					APPROVAL_TYPE_CREATED, APPROVAL_TYPE_UPDATED ->
				Module.APPROVALS;
			case ANNOUNCEMENT_PUBLISHED, ARTICLE_PUBLISHED, DOCUMENT_UPLOADED, DOCUMENT_DELETED -> Module.COLLABORATION;
			case CSV_IMPORTED -> Module.IMPORTS;
			case SEO_PAGE_CREATED, SEO_PAGE_UPDATED, SEO_PAGE_DELETED, SEO_KEYWORD_CREATED, SEO_KEYWORD_UPDATED,
					SEO_KEYWORD_DELETED, SEO_RANKING_RECORDED, SEO_RANKING_CORRECTED ->
				Module.SEO;
			case TARGET_TYPE_CREATED, TARGET_TYPE_UPDATED, TARGET_TYPE_DELETED, TARGET_CREATED, TARGET_UPDATED,
					TARGET_DELETED ->
				Module.TARGETS;
			case MARKETING_ACTIVITY_CREATED, MARKETING_ACTIVITY_UPDATED, MARKETING_ACTIVITY_DELETED,
					MARKETING_ACTIVITY_OCCURRENCE_COMPLETED, MARKETING_ACTIVITY_OCCURRENCE_SKIPPED,
					MARKETING_ACTIVITY_OCCURRENCE_REOPENED ->
				Module.ACTIVITIES;
			case EMAIL_CAMPAIGN_CREATED, EMAIL_CAMPAIGN_UPDATED, EMAIL_CAMPAIGN_DELETED, PAID_CAMPAIGN_CREATED,
					PAID_CAMPAIGN_UPDATED, PAID_CAMPAIGN_DELETED, PAID_RESULTS_RECORDED, PAID_RESULTS_CORRECTED ->
				Module.CAMPAIGNS;
			case LEAD_CREATED, LEAD_UPDATED, LEAD_STATUS_CHANGED, LEAD_DELETED -> Module.LEADS;
			case BACKLINK_CREATED, BACKLINK_UPDATED, BACKLINK_STATUS_CHANGED, BACKLINK_DELETED -> Module.BACKLINKS;
			case CONTENT_CREATED, CONTENT_UPDATED, CONTENT_STATUS_CHANGED, CONTENT_DELETED -> Module.CONTENT;
			case EMPLOYEE_CREATED, EMPLOYEE_UPDATED, EMPLOYEE_DELETED, IMPORTANT_DAY_CREATED, IMPORTANT_DAY_UPDATED,
					IMPORTANT_DAY_DELETED, MARKETING_EVENT_CREATED, MARKETING_EVENT_UPDATED, MARKETING_EVENT_DELETED,
					LINKEDIN_POST_UPDATED ->
				Module.CELEBRATIONS;
			case MARKETING_REPORT_FROZEN -> Module.REPORTS;
			case SETTING_UPDATED -> Module.SETTINGS;
			case BACKUP_COMPLETED, BACKUP_COPY_FAILED, BACKUP_FAILED, BACKUP_REMOVED, BACKUP_RESTORED, DATA_EXPORTED -> Module.BACKUPS;
			case MODULE_ENABLED, MODULE_DISABLED -> Module.MODULES;
			case CONSULTANT_CREATED, CONSULTANT_UPDATED, CONSULTANT_DELETED, CLIENT_CREATED, CLIENT_UPDATED,
					CLIENT_DELETED, ENGAGEMENT_CREATED, ENGAGEMENT_UPDATED, ENGAGEMENT_DELETED ->
				Module.BENCH;
		};
	}

	/** "TASK_STATUS_CHANGED" → "Task status changed"; a few actions read better spelled out. */
	public static String labelOf(AuditAction action) {
		return switch (action) {
			case LOGIN -> "Signed in";
			case LOGIN_FAILED -> "Sign-in failed";
			case LOGOUT -> "Signed out";
			case REFRESH_TOKEN_REUSE -> "Refresh token reused (session revoked)";
			case USER_ACCESS_CHANGED -> "User roles or permissions changed";
			case USERS_IMPORTED -> "Users imported from CSV";
			case CSV_IMPORTED -> "CSV import committed";
			case SEO_RANKING_RECORDED -> "SEO ranking recorded";
			case SEO_RANKING_CORRECTED -> "SEO ranking corrected";
			case SLA_POLICY_UPDATED -> "SLA policy updated";
			case BACKUP_REMOVED -> "Old backups removed (retention)";
			case BACKUP_RESTORED -> "Backup restored";
			case DATA_EXPORTED -> "All data exported";
			case BACKUP_COPY_FAILED -> "Backup second copy failed";
			case BREAK_GLASS_USED -> "Break-glass access used (server command line)";
			case MODULE_ENABLED -> "Department tool switched on";
			case MODULE_DISABLED -> "Department tool switched off";
			case RECORD_CREATED -> "Created";
			case RECORD_UPDATED -> "Edited";
			case RECORD_DELETED -> "Deleted";
			case RECORD_RESTORED -> "Restored";
			case RECORD_PURGED -> "Permanently deleted";
			default -> {
				String words = action.name().replace('_', ' ').toLowerCase(Locale.ROOT);
				yield Character.toUpperCase(words.charAt(0)) + words.substring(1);
			}
		};
	}

	/** "Task edited", "Marketing lead created": a record change named after its record type. */
	public static String labelOf(AuditAction action, String entityType) {
		if (!isRecordChange(action) || entityType == null) {
			return labelOf(action);
		}
		return RecordTypes.labelOf(entityType) + " " + labelOf(action).toLowerCase(Locale.ROOT);
	}

	public static Optional<AuditAction> parse(String action) {
		return Arrays.stream(AuditAction.values()).filter(a -> a.name().equals(action)).findFirst();
	}

	public static Set<AuditAction> actionsIn(Module module) {
		return Arrays.stream(AuditAction.values())
			.filter(a -> moduleOf(a) == module)
			.collect(Collectors.toUnmodifiableSet());
	}

	public static List<BriefEvent> eventsOf(AuditAction action) {
		return Arrays.stream(BriefEvent.values()).filter(e -> e.actions().contains(action)).toList();
	}

}
