package com.teamops.celebration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.teamops.celebration.dto.CelebrationDtos.LinkedInPostDto;
import com.teamops.celebration.entity.ImportantDayCategory;
import com.teamops.celebration.entity.LinkedInPostStatus;
import com.teamops.celebration.entity.MarketingEventStatus;
import com.teamops.celebration.entity.MarketingEventType;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.entity.Region;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

class CelebrationReminderServiceTest {

	/** 01:00 on 13 October in Sydney, still 12 October in India. */
	private static final Instant NOW = Instant.parse("2026-10-12T14:00:00Z");

	private static final LocalDate SYDNEY_TODAY = LocalDate.of(2026, 10, 13);

	private final OccasionFinder finder = mock(OccasionFinder.class);

	private final LinkedInPostService linkedInPosts = mock(LinkedInPostService.class);

	private final UserRepository userRepository = mock(UserRepository.class);

	private final NotificationRepository notificationRepository = mock(NotificationRepository.class);

	private final NotificationService notificationService = mock(NotificationService.class);

	private final AppSettingsService settings = mock(AppSettingsService.class);

	private final User admin = TestFixtures.user(1L, "rakesh@teamops.local", TestFixtures.role(1L, "SUPER_ADMIN"));

	private final User editor = TestFixtures.user(5L, "priya.menon@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));

	private CelebrationReminderService service;

	@BeforeEach
	void setUp() {
		service = new CelebrationReminderService(finder, linkedInPosts, userRepository, notificationRepository,
				notificationService, settings,
				new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"), "Australia/Sydney");
		when(settings.celebrationReminderDaysBefore()).thenReturn(2);
		when(userRepository.findByRoleAndStatus("SUPER_ADMIN", UserStatus.ACTIVE)).thenReturn(List.of(admin));
		when(userRepository.findActiveWithPermission("CELEBRATIONS_EDIT", UserStatus.ACTIVE))
			.thenReturn(List.of(admin, editor));
		when(linkedInPosts.forOccasions(anyCollection())).thenReturn(Map.of());
	}

	@Test
	void messagesFollowTheBrief() {
		LinkedInPostDto noPost = LinkedInPostService.dto(OccasionKind.BIRTHDAY, null);
		LinkedInPostDto dayPost = LinkedInPostService.dto(OccasionKind.IMPORTANT_DAY, null);
		assertThat(CelebrationReminderService.title(birthday(LocalDate.of(2026, 10, 14)), 2, noPost))
			.isEqualTo("Reminder: Priya Menon's birthday is in 2 days (14 October)");
		assertThat(CelebrationReminderService.title(diwali(LocalDate.of(2026, 11, 8)), 2, dayPost))
			.isEqualTo("Reminder: Diwali (India) is in 2 days. LinkedIn post: Not started");
		assertThat(CelebrationReminderService.title(birthday(LocalDate.of(2026, 10, 14)), 0, noPost))
			.isEqualTo("Today: Priya Menon's birthday (14 October)");
		assertThat(CelebrationReminderService.title(birthday(LocalDate.of(2026, 10, 14)), 1, noPost))
			.isEqualTo("Reminder: Priya Menon's birthday is tomorrow (14 October)");
		Occasion anniversary = new Occasion(OccasionKind.WORK_ANNIVERSARY, 1L, LocalDate.of(2026, 11, 3),
				LocalDate.of(2026, 11, 3), "Arun Kumar", "Arun Kumar's work anniversary", "5 years at Tritusa", 5, null,
				null, null, null, null, null, null, null, null, null);
		LinkedInPostDto drafted = new LinkedInPostDto(true, true, LinkedInPostStatus.DRAFTED, null, null, null, 0);
		assertThat(CelebrationReminderService.title(anniversary, 2, drafted)).isEqualTo(
				"Reminder: Arun Kumar's work anniversary is in 2 days (3 November): 5 years at Tritusa. LinkedIn post: Drafted");
		Occasion webinar = new Occasion(OccasionKind.EVENT, 9L, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15),
				"SAP testing", "SAP testing", null, null, Instant.parse("2026-10-15T04:00:00Z"), null, null, null, null,
				null, MarketingEventType.WEBINAR, MarketingEventStatus.CONFIRMED, null, null);
		assertThat(CelebrationReminderService.title(webinar, 2, LinkedInPostService.dto(OccasionKind.EVENT, null)))
			.isEqualTo("Reminder: SAP testing (webinar) is in 2 days (15 October). LinkedIn post: Not started");
	}

	@Test
	void remindsEditorsAndSuperAdminsDaysBeforeAndOnTheDayInSydneyTime() {
		Occasion soon = birthday(SYDNEY_TODAY.plusDays(2));
		Occasion today = diwali(SYDNEY_TODAY);
		when(finder.between(SYDNEY_TODAY.plusDays(2), SYDNEY_TODAY.plusDays(2), false)).thenReturn(List.of(soon));
		when(finder.between(SYDNEY_TODAY, SYDNEY_TODAY, false)).thenReturn(List.of(today));
		when(notificationRepository.findExistingDedupKeys(anyCollection())).thenReturn(Set.of());

		assertThat(service.sendReminders()).as("2 occasions × 2 recipients (the admin once)").isEqualTo(4);

		verify(notificationService).notify(eq(5L), eq(NotificationType.OCCASION_REMINDER),
				eq("Reminder: Priya Menon's birthday is in 2 days (15 October)"), eq("Thursday, 15 October 2026"),
				eq(NotificationService.ENTITY_EMPLOYEE), eq(1L), eq("OCCASION:BIRTHDAY:1:2026-10-15:2:5"));
		verify(notificationService).notify(eq(1L), eq(NotificationType.OCCASION_REMINDER),
				eq("Today: Diwali (India). LinkedIn post: Not started"), any(), eq(NotificationService.ENTITY_IMPORTANT_DAY),
				eq(4L), eq("OCCASION:IMPORTANT_DAY:4:2026-10-13:0:1"));
	}

	@Test
	void neverSendsTheSameReminderTwice() {
		Occasion soon = birthday(SYDNEY_TODAY.plusDays(2));
		when(finder.between(SYDNEY_TODAY.plusDays(2), SYDNEY_TODAY.plusDays(2), false)).thenReturn(List.of(soon));
		when(notificationRepository.findExistingDedupKeys(anyCollection()))
			.thenReturn(Set.of("OCCASION:BIRTHDAY:1:2026-10-15:2:1", "OCCASION:BIRTHDAY:1:2026-10-15:2:5"));

		assertThat(service.sendReminders()).isZero();
		verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void theDaysBeforeSettingDecidesTheLeadTime() {
		when(settings.celebrationReminderDaysBefore()).thenReturn(5);
		Occasion inFive = birthday(SYDNEY_TODAY.plusDays(5));
		when(finder.between(SYDNEY_TODAY.plusDays(5), SYDNEY_TODAY.plusDays(5), false)).thenReturn(List.of(inFive));
		when(notificationRepository.findExistingDedupKeys(anyCollection())).thenReturn(Set.of());

		assertThat(service.sendReminders()).isEqualTo(2);
		verify(notificationService).notify(eq(1L), any(), eq("Reminder: Priya Menon's birthday is in 5 days (18 October)"),
				any(), any(), any(), eq("OCCASION:BIRTHDAY:1:2026-10-18:5:1"));
	}

	private static Occasion birthday(LocalDate date) {
		return new Occasion(OccasionKind.BIRTHDAY, 1L, date, date, "Priya Menon", "Priya Menon's birthday",
				"Digital Marketing Manager", null, null, null, null, null, null, null, null, null, null, null);
	}

	private static Occasion diwali(LocalDate date) {
		return new Occasion(OccasionKind.IMPORTANT_DAY, 4L, date, date, "Diwali", "Diwali", "India", null, null, null,
				null, null, ImportantDayCategory.FESTIVAL, Region.INDIA, null, null, null, null);
	}

}
