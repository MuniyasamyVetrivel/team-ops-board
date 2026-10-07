package com.teamops.notification.service;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageResponse;
import com.teamops.notification.dto.NotificationResponse;
import com.teamops.notification.dto.UnreadCount;
import com.teamops.notification.entity.Notification;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.task.event.TaskAssignedEvent;

import lombok.RequiredArgsConstructor;

/**
 * In-app notifications (brief section 65). Users only ever see and change their own notifications; anything else is
 * reported as not found.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

	public static final String ENTITY_TASK = "TASK";

	public static final String ENTITY_TICKET = "TICKET";

	private final NotificationRepository repository;

	private final BusinessCalendar calendar;

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(AuthenticatedUser actor, boolean unreadOnly, Pageable pageable) {
		var page = unreadOnly ? repository.findByUserIdAndReadAtIsNull(actor.id(), pageable)
				: repository.findByUserId(actor.id(), pageable);
		return PageResponse.of(page.map(NotificationResponse::of));
	}

	@Transactional(readOnly = true)
	public UnreadCount unreadCount(AuthenticatedUser actor) {
		return new UnreadCount(repository.countByUserIdAndReadAtIsNull(actor.id()));
	}

	@Transactional
	public NotificationResponse markRead(Long id, AuthenticatedUser actor) {
		Notification notification = repository.findByIdAndUserId(id, actor.id())
			.orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found"));
		if (notification.getReadAt() == null) {
			notification.setReadAt(calendar.now());
		}
		return NotificationResponse.of(notification);
	}

	@Transactional
	public UnreadCount markAllRead(AuthenticatedUser actor) {
		repository.markAllRead(actor.id(), calendar.now());
		return new UnreadCount(0);
	}

	/** Creates a notification in the caller's transaction. */
	@Transactional
	public Notification notify(Long userId, NotificationType type, String title, String body, String entityType,
			Long entityId, String dedupKey) {
		Notification notification = new Notification();
		notification.setUserId(userId);
		notification.setType(type);
		notification.setTitle(truncate(title, Notification.MAX_TITLE_LENGTH));
		notification.setBody(truncate(body, Notification.MAX_BODY_LENGTH));
		notification.setEntityType(entityType);
		notification.setEntityId(entityId);
		notification.setDedupKey(dedupKey);
		return repository.save(notification);
	}

	@EventListener
	public void onTaskAssigned(TaskAssignedEvent event) {
		if (event.assigneeId() == null || event.assigneeId().equals(event.actorId())) {
			return;
		}
		notify(event.assigneeId(), NotificationType.TASK_ASSIGNED, event.code() + " assigned to you",
				event.actorName() + " assigned you \"" + event.title() + "\"", ENTITY_TASK, event.taskId(), null);
	}

	static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

}
