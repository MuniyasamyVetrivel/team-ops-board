package com.teamops.notification.service;

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
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.task.repository.TaskReminderRow;
import com.teamops.task.repository.TaskRepository;

class TaskReminderServiceTest {

	/** 20:00 UTC on 7 October = 01:30 IST on 8 October: "today" is the 8th in the business zone. */
	private static final Instant NOW = Instant.parse("2026-10-07T20:00:00Z");

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

	private final TaskRepository taskRepository = mock(TaskRepository.class);

	private final NotificationRepository notificationRepository = mock(NotificationRepository.class);

	private final NotificationService notificationService = mock(NotificationService.class);

	private TaskReminderService service;

	@BeforeEach
	void setUp() {
		service = new TaskReminderService(taskRepository, notificationRepository, notificationService,
				new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"));
	}

	@Test
	void remindersUseTheBusinessDateAndCoverOverdueTodayAndTomorrow() {
		when(taskRepository.findReminderCandidates(any(), eq(TODAY.plusDays(1)))).thenReturn(
				List.of(row(1L, TODAY.minusDays(3)), row(2L, TODAY), row(3L, TODAY.plusDays(1))));
		when(notificationRepository.findExistingDedupKeys(anyCollection())).thenReturn(Set.of());

		assertThat(service.sendDueReminders()).isEqualTo(3);

		verify(notificationService).notify(4L, NotificationType.TASK_OVERDUE, "TSK-1 is overdue", "Task 1", "TASK", 1L,
				"TASK_OVERDUE:1:2026-10-05:4");
		verify(notificationService).notify(4L, NotificationType.TASK_DUE_SOON, "TSK-2 is due today", "Task 2", "TASK",
				2L, "TASK_DUE_SOON:2:2026-10-08:4");
		verify(notificationService).notify(4L, NotificationType.TASK_DUE_SOON, "TSK-3 is due tomorrow", "Task 3",
				"TASK", 3L, "TASK_DUE_SOON:3:2026-10-09:4");
	}

	@Test
	void alreadySentRemindersAreSkipped() {
		when(taskRepository.findReminderCandidates(any(), any())).thenReturn(List.of(row(1L, TODAY.minusDays(3))));
		when(notificationRepository.findExistingDedupKeys(anyCollection()))
			.thenReturn(Set.of("TASK_OVERDUE:1:2026-10-05:4"));

		assertThat(service.sendDueReminders()).isZero();
		verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void aNewDueDateOrAssigneeGetsAFreshKey() {
		String original = TaskReminderService.dedupKey(row(1L, TODAY.minusDays(3)), TODAY);
		assertThat(TaskReminderService.dedupKey(row(1L, TODAY.minusDays(1)), TODAY)).isNotEqualTo(original);
		assertThat(TaskReminderService.typeFor(TODAY, TODAY)).isEqualTo(NotificationType.TASK_DUE_SOON);
		assertThat(TaskReminderService.typeFor(TODAY.minusDays(1), TODAY)).isEqualTo(NotificationType.TASK_OVERDUE);
	}

	private static TaskReminderRow row(Long id, LocalDate due) {
		return new TaskReminderRow() {

			@Override
			public Long getId() {
				return id;
			}

			@Override
			public String getCode() {
				return "TSK-" + id;
			}

			@Override
			public String getTitle() {
				return "Task " + id;
			}

			@Override
			public Long getAssigneeId() {
				return 4L;
			}

			@Override
			public LocalDate getDueDate() {
				return due;
			}

		};
	}

}
