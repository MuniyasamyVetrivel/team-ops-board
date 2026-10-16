package com.teamops.vendor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.teamops.module.service.ModuleAccessService;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorContract;
import com.teamops.vendor.repository.VendorContractRepository;

/** Renewal reminders 30 and 7 days ahead, to the module's recipients, once each. */
class VendorReminderServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

	private final VendorContractRepository contracts = mock(VendorContractRepository.class);

	private final ModuleAccessService moduleAccess = mock(ModuleAccessService.class);

	private final NotificationRepository notifications = mock(NotificationRepository.class);

	private final NotificationService notificationService = mock(NotificationService.class);

	private final VendorReminderService service = new VendorReminderService(contracts, moduleAccess, notifications,
			notificationService,
			new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"));

	private final User rakesh = TestFixtures.user(1L, "rakesh@teamops.local", TestFixtures.role(1L, "SUPER_ADMIN"));

	private final User hr = TestFixtures.user(14L, "hr@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));

	private Vendor atlassian;

	@BeforeEach
	void setUp() {
		atlassian = new Vendor();
		ReflectionTestUtils.setField(atlassian, "id", 5L);
		atlassian.setName("Atlassian");
		when(moduleAccess.recipients("vendors")).thenReturn(List.of(rakesh, hr));
	}

	@Test
	void eachRecipientIsRemindedOncePerContractRenewalDateAndLeadTime() {
		VendorContract jira = contract(90L, "Jira Cloud", "2026-11-06", true, 30);
		when(contracts.findRenewalsBetween(TODAY, TODAY.plusDays(30))).thenReturn(List.of(jira));
		when(notifications.findExistingDedupKeys(anyCollection()))
			.thenReturn(Set.of("CONTRACT_RENEWAL:90:2026-11-06:30:1"));

		assertThat(service.sendReminders()).isEqualTo(1);

		verify(notificationService).notify(14L, NotificationType.CONTRACT_RENEWAL, "Renewal in 20 days: Atlassian – Jira Cloud",
				"Atlassian's contract \"Jira Cloud\" renews automatically on Friday, 6 November 2026. To cancel or "
						+ "change it, give notice by Wednesday, 7 October 2026.",
				"VENDOR_CONTRACT", 90L, "CONTRACT_RENEWAL:90:2026-11-06:30:14");
		verify(notificationService, never()).notify(eq(1L), any(), any(), any(), any(), any(), any());
	}

	@Test
	void theSevenDayReminderIsASeparateOne() {
		when(contracts.findRenewalsBetween(TODAY, TODAY.plusDays(30)))
			.thenReturn(List.of(contract(91L, "Confluence", "2026-10-24", false, null)));
		when(notifications.findExistingDedupKeys(anyCollection())).thenReturn(Set.of());

		assertThat(service.sendReminders()).isEqualTo(2);

		verify(notificationService).notify(eq(1L), eq(NotificationType.CONTRACT_RENEWAL), eq("Renewal in 7 days: Atlassian – Confluence"),
				eq("Atlassian's contract \"Confluence\" is due for renewal on Saturday, 24 October 2026."),
				eq("VENDOR_CONTRACT"), eq(91L), eq("CONTRACT_RENEWAL:91:2026-10-24:7:1"));
	}

	@Test
	void withoutRecipientsNothingIsSent() {
		when(moduleAccess.recipients("vendors")).thenReturn(List.of());

		assertThat(service.sendReminders()).isZero();

		verifyNoInteractions(contracts, notificationService);
	}

	@Test
	void contractsWithoutARenewalDateEndRatherThanRenew() {
		VendorContract ending = contract(92L, "Support", null, false, null);
		ending.setEndDate(TODAY.plusDays(1));

		assertThat(VendorReminderService.title(ending, 0)).isEqualTo("Ends today: Atlassian – Support");
		assertThat(VendorReminderService.title(ending, 1)).isEqualTo("Ends tomorrow: Atlassian – Support");
		assertThat(VendorReminderService.title(ending, 30)).isEqualTo("Ends in 30 days: Atlassian – Support");
		assertThat(VendorReminderService.body(ending, TODAY.plusDays(1)))
			.isEqualTo("Atlassian's contract \"Support\" ends on Sunday, 18 October 2026.");
		VendorContract renewing = contract(93L, "Jira", "2026-10-17", false, null);
		assertThat(VendorReminderService.title(renewing, 0)).isEqualTo("Renews today: Atlassian – Jira");
	}

	private VendorContract contract(Long id, String title, String renewal, boolean autoRenew, Integer noticeDays) {
		VendorContract contract = new VendorContract();
		ReflectionTestUtils.setField(contract, "id", id);
		contract.setVendor(atlassian);
		contract.setTitle(title);
		contract.setStartDate(LocalDate.of(2026, 1, 1));
		contract.setRenewalDate(renewal == null ? null : LocalDate.parse(renewal));
		contract.setAutoRenew(autoRenew);
		contract.setNoticePeriodDays(noticeDays);
		return contract;
	}

}
