package com.teamops.common.audit;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
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

	/** JSON document; serialised by {@link AuditService}. */
	@Column(name = "details", columnDefinition = "json")
	private String details;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

}
