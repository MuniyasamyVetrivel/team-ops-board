package com.teamops.notification.dto;

import java.time.Instant;

import com.teamops.notification.entity.Notification;
import com.teamops.notification.entity.NotificationType;

public record NotificationResponse(Long id, NotificationType type, String title, String body, String entityType,
		Long entityId, boolean read, Instant createdAt) {

	public static NotificationResponse of(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
				notification.getBody(), notification.getEntityType(), notification.getEntityId(),
				notification.getReadAt() != null, notification.getCreatedAt());
	}

}
