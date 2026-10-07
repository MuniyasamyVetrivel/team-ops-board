package com.teamops.notification.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One in-app notification for one user. The recipient is a plain id: notifications are always read per user. */
@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification {

	public static final int MAX_TITLE_LENGTH = 200;

	public static final int MAX_BODY_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "type", nullable = false, length = 40)
	private NotificationType type;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "body", length = 500)
	private String body;

	@Column(name = "entity_type", length = 40)
	private String entityType;

	@Column(name = "entity_id")
	private Long entityId;

	@Column(name = "dedup_key", length = 120)
	private String dedupKey;

	@Column(name = "read_at")
	private Instant readAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

}
