package com.teamops.common.audit;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One audit entry. Entries are append-only: the entity is immutable, the repository offers no update or delete, and
 * no endpoint changes them. Record changes are written by {@link EntityChangeAuditor}, business events by
 * {@link AuditService}.
 */
@Getter
@Setter
@Entity
@Immutable
@AuditIgnore
@Table(name = "audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "actor_id")
	private Long actorId;

	@Column(name = "action", nullable = false, length = 60)
	private String action;

	@Column(name = "entity_type", length = 60)
	private String entityType;

	@Column(name = "entity_id")
	private Long entityId;

	/** The record's name when it was logged ("TSK-000123", a title, a person's name). */
	@Column(name = "entity_label", length = 255)
	private String entityLabel;

	/** The record this one belongs to (a comment's task), so it shows in the parent's history too. */
	@Column(name = "parent_type", length = 60)
	private String parentType;

	@Column(name = "parent_id")
	private Long parentId;

	/** JSON document; serialised by {@link AuditService}. */
	@Column(name = "details", columnDefinition = "json")
	private String details;

	/** The details hold money amounts, shown only to users who can see finance. */
	@Column(name = "finance", nullable = false)
	private boolean finance;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

}
