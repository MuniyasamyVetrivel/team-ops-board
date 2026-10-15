package com.teamops.common.persistence;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * Who created and last changed a record, and when (timestamps in UTC). Every record table carries these four columns;
 * Hibernate fills them on insert and update, so services never set them. A null user means the system (a background
 * job, or a row that existed before the columns did).
 * <p>
 * An entity that also maps {@code created_by} or {@code updated_by} as a {@code User} relation keeps that relation
 * read-only ({@code insertable = false, updatable = false}) and passes an explicit creator through
 * {@link #assignCreatedBy(Long)}.
 */
@Getter
@MappedSuperclass
public abstract class AuditedEntity {

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@CurrentActor
	@Column(name = "created_by", updatable = false)
	private Long createdById;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@CurrentActor(onUpdate = true)
	@Column(name = "updated_by")
	private Long updatedById;

	/** Records a creator other than the signed-in user (kept on insert; ignored once the row exists). */
	protected void assignCreatedBy(Long userId) {
		this.createdById = userId;
	}

}
