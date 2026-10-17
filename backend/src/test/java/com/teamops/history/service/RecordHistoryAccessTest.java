package com.teamops.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.approval.service.ApprovalService;
import com.teamops.bench.service.AllocationService;
import com.teamops.bench.service.ClientService;
import com.teamops.bench.service.ConsultantService;
import com.teamops.bench.service.EngagementService;
import com.teamops.calendar.service.CalendarService;
import com.teamops.celebration.service.EmployeeService;
import com.teamops.celebration.service.ImportantDayService;
import com.teamops.celebration.service.MarketingEventService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.service.DepartmentService;
import com.teamops.document.service.DocumentService;
import com.teamops.finance.service.BudgetService;
import com.teamops.finance.service.ExchangeRateService;
import com.teamops.finance.service.ExpenseService;
import com.teamops.finance.service.LicenceService;
import com.teamops.marketing.activity.service.ActivityService;
import com.teamops.marketing.backlink.service.BacklinkService;
import com.teamops.marketing.content.service.ContentService;
import com.teamops.marketing.email.service.EmailCampaignService;
import com.teamops.marketing.lead.service.LeadService;
import com.teamops.marketing.paid.service.PaidCampaignService;
import com.teamops.marketing.target.service.TargetService;
import com.teamops.module.dto.ModuleDtos.Tool;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.project.service.ProjectService;
import com.teamops.support.SliceAuth;
import com.teamops.task.service.TaskService;
import com.teamops.ticket.service.TicketService;
import com.teamops.user.service.UserService;
import com.teamops.vendor.service.VendorContractService;
import com.teamops.vendor.service.VendorService;

/** History is visible exactly to whoever may open the record. */
class RecordHistoryAccessTest {

	private final TaskService tasks = mock(TaskService.class);

	private final UserService users = mock(UserService.class);

	private final LeadService leads = mock(LeadService.class);

	private final ModuleAccessService moduleAccess = mock(ModuleAccessService.class);

	private final VendorService vendors = mock(VendorService.class);

	private final RecordHistoryAccess access = new RecordHistoryAccess(tasks, mock(TicketService.class),
			mock(ProjectService.class), mock(ApprovalService.class), mock(DepartmentService.class), users,
			mock(DocumentService.class), mock(CalendarService.class), mock(EmployeeService.class),
			mock(ImportantDayService.class), mock(MarketingEventService.class), mock(ActivityService.class),
			mock(BacklinkService.class), mock(ContentService.class), leads, mock(PaidCampaignService.class),
			mock(EmailCampaignService.class), mock(TargetService.class), moduleAccess,
			mock(ConsultantService.class), mock(ClientService.class), mock(EngagementService.class),
			mock(AllocationService.class), vendors, mock(VendorContractService.class),
			mock(LicenceService.class), mock(ExchangeRateService.class), mock(ExpenseService.class),
			mock(BudgetService.class));

	@Test
	void aTaskUsesTheTaskReadCheck() {
		access.check("TASK", 5L, SliceAuth.EMPLOYEE);
		verify(tasks).get(5L, SliceAuth.EMPLOYEE);

		when(tasks.get(6L, SliceAuth.EMPLOYEE)).thenThrow(ApiException.notFound("TASK_NOT_FOUND", "Task not found"));
		assertThatThrownBy(() -> access.check("TASK", 6L, SliceAuth.EMPLOYEE)).isInstanceOf(ApiException.class);
	}

	@Test
	void withoutTheModulePermissionTheRecordIsNotFound() {
		assertThatThrownBy(() -> access.check("TASK", 5L, SliceAuth.NOBODY))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("RECORD_NOT_FOUND"));
		assertThatThrownBy(() -> access.check("MARKETING_LEAD", 5L, SliceAuth.EMPLOYEE))
			.isInstanceOf(ApiException.class);
		verifyNoInteractions(tasks, leads);
	}

	@Test
	void peopleSeeTheirOwnUserHistoryAndUserAdminsSeeEveryones() {
		AuthenticatedUser employee = SliceAuth.EMPLOYEE;
		assertThatCode(() -> access.check("USER", employee.id(), employee)).doesNotThrowAnyException();
		assertThatThrownBy(() -> access.check("USER", 99L, employee)).isInstanceOf(ApiException.class);
		assertThatCode(() -> access.check("USER", 99L, SliceAuth.USER_ADMIN)).doesNotThrowAnyException();
	}

	@Test
	void unknownTypesAreOnlyForAuditViewers() {
		assertThatThrownBy(() -> access.check("TASK_COMMENT", 1L, SliceAuth.EMPLOYEE))
			.isInstanceOf(ApiException.class);
		AuthenticatedUser auditor = new AuthenticatedUser(30L, "auditor@teamops.local", "Auditor", 1L,
				Set.of("EMPLOYEE"), Set.of("AUDIT_VIEW"));
		assertThatCode(() -> access.check("TASK_COMMENT", 1L, auditor)).doesNotThrowAnyException();
		assertThatCode(() -> access.check("TASK", 1L, auditor)).doesNotThrowAnyException();
		verifyNoInteractions(tasks);
	}

	@Test
	void theMainRecordTypesHaveAHistoryTab() {
		assertThat(access.types()).contains("TASK", "TICKET", "PROJECT", "APPROVAL", "DEPARTMENT", "USER", "DOCUMENT",
				"CALENDAR_EVENT", "EMPLOYEE", "IMPORTANT_DAY", "MARKETING_EVENT", "SEO_PAGE", "SEO_KEYWORD",
				"MARKETING_TARGET", "MARKETING_ACTIVITY", "EMAIL_CAMPAIGN", "PAID_CAMPAIGN", "MARKETING_LEAD",
				"BACKLINK", "CONTENT_ITEM", "ARTICLE", "ANNOUNCEMENT", "SETTING", "CONSULTANT", "ALLOCATION", "VENDOR",
				"VENDOR_CONTRACT", "FINANCE_LICENCE", "EXCHANGE_RATE", "FINANCE_EXPENSE", "FINANCE_BUDGET");
	}

	@Test
	void vendorHistoryNeedsTheVendorsModuleSwitchedOnForTheViewer() {
		AuthenticatedUser historian = new AuthenticatedUser(14L, "hr@teamops.local", "HR", 2L, Set.of("EMPLOYEE"),
				Set.of("HISTORY_VIEW", "VENDOR_VIEW"));
		when(moduleAccess.toolsFor(historian)).thenReturn(List.of());
		assertThatThrownBy(() -> access.check("VENDOR", 5L, historian)).isInstanceOf(ApiException.class);
		verifyNoInteractions(vendors);

		when(moduleAccess.toolsFor(historian)).thenReturn(List.of(new Tool("vendors", "Vendors", "Store", "", false)));
		access.check("VENDOR", 5L, historian);
		verify(vendors).get(5L);
	}

}
