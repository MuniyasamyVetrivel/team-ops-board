package com.teamops.bench.service;

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

import com.teamops.bench.entity.Allocation;
import com.teamops.bench.entity.Consultant;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.repository.AllocationRepository;
import com.teamops.celebration.entity.Employee;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;

/** Roll-off reminders 30 and 14 days ahead, to the module's recipients, once each. */
class BenchReminderServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

	private final AllocationRepository allocations = mock(AllocationRepository.class);

	private final ModuleAccessService moduleAccess = mock(ModuleAccessService.class);

	private final NotificationRepository notifications = mock(NotificationRepository.class);

	private final NotificationService notificationService = mock(NotificationService.class);

	private final BenchReminderService service = new BenchReminderService(allocations, moduleAccess, notifications,
			notificationService,
			new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-16T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"));

	private final User rakesh = TestFixtures.user(1L, "rakesh@teamops.local", TestFixtures.role(1L, "SUPER_ADMIN"));

	private final User presales = TestFixtures.user(12L, "presales@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));

	private Consultant priya;

	@BeforeEach
	void setUp() {
		Employee employee = new Employee();
		employee.setFirstName("Priya");
		employee.setLastName("Menon");
		priya = new Consultant();
		ReflectionTestUtils.setField(priya, "id", 3L);
		priya.setEmployee(employee);
		when(moduleAccess.recipients("bench")).thenReturn(List.of(rakesh, presales));
	}

	@Test
	void theLeadTimeIsTheSmallestOneTheAllocationIsWithin() {
		assertThat(BenchReminderService.leadFor(30)).isEqualTo(30);
		assertThat(BenchReminderService.leadFor(15)).isEqualTo(30);
		assertThat(BenchReminderService.leadFor(14)).isEqualTo(14);
		assertThat(BenchReminderService.leadFor(0)).isEqualTo(14);
	}

	@Test
	void eachRecipientIsRemindedOncePerAllocationEndDateAndLeadTime() {
		Allocation in20Days = BenchServiceTest.allocation(priya, "2026-11-05");
		when(allocations.findByEndDateBetweenOrderByEndDateAscIdAsc(TODAY, TODAY.plusDays(30)))
			.thenReturn(List.of(in20Days));
		when(notifications.findExistingDedupKeys(anyCollection()))
			.thenReturn(Set.of("BENCH_ROLL_OFF:90:2026-11-05:30:1"));

		assertThat(service.sendReminders()).isEqualTo(1);

		verify(notificationService).notify(12L, NotificationType.BENCH_ROLL_OFF,
				"Roll-off in 20 days: Priya Menon (Acme – S/4 rollout)",
				"Priya Menon's allocation as Test Lead (50%, non-billable) ends on Thursday, 5 November 2026.",
				"ALLOCATION", 90L, "BENCH_ROLL_OFF:90:2026-11-05:30:12");
		verify(notificationService, never()).notify(eq(1L), any(), any(), any(), any(), any(), any());
	}

	@Test
	void consultantsWhoLeftAndModulesWithoutRecipientsSendNothing() {
		priya.setEmploymentStatus(EmploymentStatus.LEFT);
		when(allocations.findByEndDateBetweenOrderByEndDateAscIdAsc(any(), any()))
			.thenReturn(List.of(BenchServiceTest.allocation(priya, "2026-10-17")));
		assertThat(service.sendReminders()).isZero();

		when(moduleAccess.recipients("bench")).thenReturn(List.of());
		assertThat(service.sendReminders()).isZero();
		verifyNoInteractions(notificationService);
	}

	@Test
	void titlesSayWhenTheConsultantRollsOff() {
		Allocation allocation = BenchServiceTest.allocation(priya, "2026-10-16");
		assertThat(BenchReminderService.title(allocation, 0)).isEqualTo("Rolls off today: Priya Menon (Acme – S/4 rollout)");
		assertThat(BenchReminderService.title(allocation, 1)).startsWith("Rolls off tomorrow: ");
		assertThat(BenchReminderService.title(allocation, 14)).startsWith("Roll-off in 14 days: ");
	}

}
