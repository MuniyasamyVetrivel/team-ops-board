package com.teamops.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.notification.entity.Notification;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.support.SliceAuth;
import com.teamops.task.event.TaskAssignedEvent;

class NotificationServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

	private final NotificationRepository repository = mock(NotificationRepository.class);

	private NotificationService service;

	@BeforeEach
	void setUp() {
		service = new NotificationService(repository,
				new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void assignmentNotifiesTheNewAssignee() {
		service.onTaskAssigned(new TaskAssignedEvent(42L, "TSK-000042", "Fix header", 4L, 1L, "Rakesh"));

		verify(repository).save(argThat((Notification n) -> n.getUserId().equals(4L)
				&& n.getType() == NotificationType.TASK_ASSIGNED && n.getTitle().equals("TSK-000042 assigned to you")
				&& n.getBody().equals("Rakesh assigned you \"Fix header\"") && "TASK".equals(n.getEntityType())
				&& n.getEntityId().equals(42L) && n.getDedupKey() == null));
	}

	@Test
	void selfAssignmentNotifiesNobody() {
		service.onTaskAssigned(new TaskAssignedEvent(42L, "TSK-000042", "Fix header", 4L, 4L, "Karthik"));

		verify(repository, never()).save(any());
	}

	@Test
	void longTextIsTruncatedToTheColumnSize() {
		String title = "x".repeat(300);

		Notification saved = service.notify(4L, NotificationType.TASK_OVERDUE, title, null, null, null, null);

		assertThat(saved.getTitle()).hasSize(Notification.MAX_TITLE_LENGTH).endsWith("…");
	}

	@Test
	void markReadOnlyFindsTheCallersOwnNotifications() {
		when(repository.findByIdAndUserId(7L, SliceAuth.EMPLOYEE.id())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.markRead(7L, SliceAuth.EMPLOYEE)).isInstanceOfSatisfying(ApiException.class,
				ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(ex.getCode()).isEqualTo("NOTIFICATION_NOT_FOUND");
				});
	}

	@Test
	void markReadStampsTheTimeOnce() {
		Notification notification = new Notification();
		notification.setType(NotificationType.TASK_ASSIGNED);
		when(repository.findByIdAndUserId(7L, 4L)).thenReturn(Optional.of(notification));

		assertThat(service.markRead(7L, SliceAuth.EMPLOYEE).read()).isTrue();
		assertThat(notification.getReadAt()).isEqualTo(NOW);
	}

}
