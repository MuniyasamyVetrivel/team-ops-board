package com.teamops.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.finance.entity.BillingCycle;
import com.teamops.finance.entity.FinanceLicence;
import com.teamops.finance.entity.LicenceAssignment;
import com.teamops.finance.entity.LicenceStatus;
import com.teamops.finance.repository.FinanceLicenceRepository;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;
import com.teamops.vendor.entity.Vendor;

/** Licence renewal reminders 30 and 7 days ahead, to the Finance Tracker's recipients, once each. */
class FinanceReminderServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

	private final FinanceLicenceRepository licences = mock(FinanceLicenceRepository.class);

	private final ModuleAccessService moduleAccess = mock(ModuleAccessService.class);

	private final NotificationRepository notifications = mock(NotificationRepository.class);

	private final NotificationService notificationService = mock(NotificationService.class);

	private final FinanceReminderService service = new FinanceReminderService(licences, moduleAccess, notifications,
			notificationService,
			new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"));

	private final User rakesh = TestFixtures.user(1L, "rakesh@teamops.local", TestFixtures.role(1L, "SUPER_ADMIN"));

	private final User finance = TestFixtures.user(15L, "finance@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));

	@BeforeEach
	void setUp() {
		when(moduleAccess.recipients("finance")).thenReturn(List.of(rakesh, finance));
	}

	@Test
	void eachRecipientIsRemindedOncePerLicenceRenewalDateAndLeadTime() {
		FinanceLicence jira = licence(30L, "Jira Cloud", "2026-11-06", LicenceStatus.ACTIVE);
		when(licences.findRenewalsBetween(eq(TODAY), eq(TODAY.plusDays(30)), any())).thenReturn(List.of(jira));
		when(notifications.findExistingDedupKeys(anyCollection())).thenReturn(Set.of("LICENCE_RENEWAL:30:2026-11-06:30:1"));

		assertThat(service.sendReminders()).isEqualTo(1);

		verify(notificationService).notify(15L, NotificationType.LICENCE_RENEWAL, "Renewal in 20 days: Jira Cloud (Atlassian)",
				"Jira Cloud renews on Friday, 6 November 2026. 1 of 20 seats are in use. Cost: AUD 9600.00 a year.",
				"FINANCE_LICENCE", 30L, "LICENCE_RENEWAL:30:2026-11-06:30:15");
		verify(notificationService, never()).notify(eq(1L), any(), any(), any(), any(), any(), any());
	}

	@Test
	void onlyActiveAndTrialLicencesAreReminded() {
		when(licences.findRenewalsBetween(any(), any(), any())).thenReturn(List.of());
		service.sendReminders();
		verify(licences).findRenewalsBetween(TODAY, TODAY.plusDays(30), Set.of(LicenceStatus.ACTIVE, LicenceStatus.TRIAL));
	}

	@Test
	void trialsEndRatherThanRenew() {
		FinanceLicence figma = licence(31L, "Figma", "2026-10-18", LicenceStatus.TRIAL);
		assertThat(FinanceReminderService.title(figma, 0)).isEqualTo("Trial ends today: Figma (Atlassian)");
		assertThat(FinanceReminderService.title(figma, 1)).isEqualTo("Trial ends tomorrow: Figma (Atlassian)");
		assertThat(FinanceReminderService.title(figma, 7)).isEqualTo("Trial ends in 7 days: Figma (Atlassian)");
		assertThat(FinanceReminderService.body(figma)).startsWith("Figma's trial ends on Sunday, 18 October 2026.");
	}

	@Test
	void withoutRecipientsNothingIsSent() {
		when(moduleAccess.recipients("finance")).thenReturn(List.of());
		assertThat(service.sendReminders()).isZero();
		verifyNoInteractions(licences, notificationService);
	}

	private FinanceLicence licence(Long id, String name, String renewal, LicenceStatus status) {
		Vendor atlassian = new Vendor();
		atlassian.setName("Atlassian");
		FinanceLicence licence = new FinanceLicence();
		ReflectionTestUtils.setField(licence, "id", id);
		licence.setName(name);
		licence.setVendor(atlassian);
		licence.setStatus(status);
		licence.setRenewalDate(LocalDate.parse(renewal));
		licence.setSeatsPaid(20);
		licence.setBillingCycle(BillingCycle.YEARLY);
		licence.setCost(new BigDecimal("9600.00"));
		licence.setCurrency("AUD");
		LicenceAssignment seat = new LicenceAssignment();
		seat.setLicence(licence);
		seat.setUser(finance);
		licence.getAssignments().add(seat);
		return licence;
	}

}
