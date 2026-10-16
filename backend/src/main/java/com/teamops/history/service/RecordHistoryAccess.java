package com.teamops.history.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.teamops.announcement.entity.Announcement;
import com.teamops.approval.entity.Approval;
import com.teamops.approval.entity.ApprovalType;
import com.teamops.approval.service.ApprovalService;
import com.teamops.calendar.entity.CalendarEvent;
import com.teamops.calendar.service.CalendarService;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.ImportantDay;
import com.teamops.celebration.entity.MarketingEvent;
import com.teamops.celebration.service.EmployeeService;
import com.teamops.celebration.service.ImportantDayService;
import com.teamops.celebration.service.MarketingEventService;
import com.teamops.common.audit.RecordTypes;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSetting;
import com.teamops.department.entity.Department;
import com.teamops.department.service.DepartmentService;
import com.teamops.document.entity.Document;
import com.teamops.document.service.DocumentService;
import com.teamops.knowledge.entity.KnowledgeArticle;
import com.teamops.marketing.activity.entity.MarketingActivity;
import com.teamops.marketing.activity.service.ActivityService;
import com.teamops.marketing.backlink.entity.Backlink;
import com.teamops.marketing.backlink.service.BacklinkService;
import com.teamops.marketing.content.entity.ContentItem;
import com.teamops.marketing.content.service.ContentService;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.service.EmailCampaignService;
import com.teamops.marketing.lead.entity.MarketingLead;
import com.teamops.marketing.lead.service.LeadService;
import com.teamops.marketing.paid.entity.PaidCampaign;
import com.teamops.marketing.paid.service.PaidCampaignService;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.entity.SeoPage;
import com.teamops.marketing.target.entity.MarketingTarget;
import com.teamops.marketing.target.entity.TargetType;
import com.teamops.marketing.target.service.TargetService;
import com.teamops.module.entity.DepartmentModule;
import com.teamops.project.entity.Project;
import com.teamops.project.service.ProjectService;
import com.teamops.sla.entity.SlaPolicy;
import com.teamops.task.entity.Task;
import com.teamops.task.service.TaskService;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.service.TicketService;
import com.teamops.user.entity.PermissionCodes;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;
import com.teamops.user.service.UserService;

/**
 * Which records' history a HISTORY_VIEW holder may read (the controller requires that permission, Super Admin only by
 * default): exactly the records they may open. Each record type reuses its module's own read check (the permission
 * its endpoint requires plus the service's scoping, which answers 404 for records the user cannot see), so history
 * never shows more than the record page does. Audit viewers (AUDIT_VIEW) may read any record's history, including
 * types listed nowhere here and records that no longer exist.
 */
@Component
public class RecordHistoryAccess {

	@FunctionalInterface
	private interface Check {

		void verify(Long id, AuthenticatedUser viewer);

	}

	private final Map<String, Check> checks = new HashMap<>();

	public RecordHistoryAccess(TaskService tasks, TicketService tickets, ProjectService projects,
			ApprovalService approvals, DepartmentService departments, UserService users, DocumentService documents,
			CalendarService calendar, EmployeeService employees, ImportantDayService importantDays,
			MarketingEventService events, ActivityService activities, BacklinkService backlinks,
			ContentService content, LeadService leads, PaidCampaignService paidCampaigns,
			EmailCampaignService emailCampaigns, TargetService targets) {
		register(Task.class, (id, u) -> {
			require(u, "TASK_VIEW");
			tasks.get(id, u);
		});
		register(Ticket.class, (id, u) -> {
			require(u, "TICKET_VIEW");
			tickets.get(id, u);
		});
		register(Project.class, (id, u) -> {
			require(u, "PROJECT_VIEW");
			projects.get(id, u);
		});
		register(Approval.class, (id, u) -> {
			require(u, "APPROVAL_VIEW");
			approvals.get(id, u);
		});
		register(ApprovalType.class, (id, u) -> require(u, "APPROVAL_VIEW", "APPROVAL_CONFIGURE"));
		register(SlaPolicy.class, (id, u) -> require(u, "TICKET_VIEW", "SLA_MANAGE"));
		register(Department.class, (id, u) -> {
			requireAny(u, PermissionCodes.TEAM_VIEW, PermissionCodes.DEPARTMENT_MANAGE);
			departments.get(id, u);
		});
		register(User.class, (id, u) -> {
			if (!Objects.equals(id, u.id())) {
				require(u, PermissionCodes.USER_MANAGE);
			}
			users.get(id);
		});
		register(Role.class, (id, u) -> require(u, PermissionCodes.PERMISSION_MANAGE));
		register(AppSetting.class, (id, u) -> require(u, "SETTINGS_MANAGE"));
		register(DepartmentModule.class, (id, u) -> require(u, PermissionCodes.MODULE_MANAGE));
		register(Document.class, (id, u) -> {
			require(u, "DOCUMENT_VIEW");
			documents.get(id, u);
		});
		register(KnowledgeArticle.class, (id, u) -> require(u, "KB_VIEW", "KB_EDIT"));
		register(Announcement.class, (id, u) -> require(u, "ANNOUNCEMENT_MANAGE"));
		register(CalendarEvent.class, (id, u) -> {
			require(u, "CALENDAR_VIEW");
			calendar.get(id, u);
		});
		register(Employee.class, (id, u) -> employees.get(id, u));
		register(ImportantDay.class, (id, u) -> importantDays.get(id, u));
		register(MarketingEvent.class, (id, u) -> events.get(id, u));
		register(SeoPage.class, (id, u) -> require(u, "MARKETING_VIEW", "SEO_VIEW"));
		register(SeoKeyword.class, (id, u) -> require(u, "MARKETING_VIEW", "SEO_VIEW"));
		register(TargetType.class, (id, u) -> require(u, "MARKETING_VIEW", "TARGET_VIEW"));
		register(MarketingTarget.class, (id, u) -> {
			require(u, "MARKETING_VIEW", "TARGET_VIEW");
			targets.get(id, u);
		});
		register(MarketingActivity.class, (id, u) -> {
			require(u, "MARKETING_VIEW");
			activities.get(id, u);
		});
		register(EmailCampaign.class, (id, u) -> {
			require(u, "MARKETING_VIEW", "CAMPAIGN_VIEW");
			emailCampaigns.get(id);
		});
		register(PaidCampaign.class, (id, u) -> {
			require(u, "MARKETING_VIEW", "CAMPAIGN_VIEW");
			paidCampaigns.get(id, u);
		});
		register(MarketingLead.class, (id, u) -> {
			require(u, "MARKETING_VIEW", "LEAD_VIEW");
			leads.get(id, u);
		});
		register(Backlink.class, (id, u) -> {
			require(u, "MARKETING_VIEW", "BACKLINK_VIEW");
			backlinks.get(id, u);
		});
		register(ContentItem.class, (id, u) -> {
			require(u, "MARKETING_VIEW", "CONTENT_VIEW");
			content.get(id, u);
		});
	}

	/** Record types whose history ordinary users can open (audit viewers can open any). */
	public Set<String> types() {
		return Set.copyOf(checks.keySet());
	}

	/** Passes when the viewer may see the record; otherwise answers 404, as the record page would. */
	public void check(String type, Long id, AuthenticatedUser viewer) {
		if (viewer.hasPermission(PermissionCodes.AUDIT_VIEW)) {
			return;
		}
		Check check = checks.get(type);
		if (check == null) {
			throw notFound();
		}
		check.verify(id, viewer);
	}

	private void register(Class<?> entity, Check check) {
		checks.put(RecordTypes.typeOf(entity), check);
	}

	private static void require(AuthenticatedUser viewer, String... permissions) {
		for (String permission : permissions) {
			if (!viewer.hasPermission(permission)) {
				throw notFound();
			}
		}
	}

	private static void requireAny(AuthenticatedUser viewer, String... permissions) {
		for (String permission : permissions) {
			if (viewer.hasPermission(permission)) {
				return;
			}
		}
		throw notFound();
	}

	private static ApiException notFound() {
		return ApiException.notFound("RECORD_NOT_FOUND", "Record not found");
	}

}
