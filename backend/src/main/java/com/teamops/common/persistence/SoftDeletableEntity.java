package com.teamops.common.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * A record that goes to the recycle bin when deleted: {@code deleted_at} and {@code deleted_by} are set and the row
 * stays. Each subclass also carries {@code @SQLRestriction("deleted_at IS NULL")}, so Hibernate hides deleted rows from
 * every query, load and collection; hand-written SQL must filter them itself. Delete through
 * {@code RecycleBinService.moveToBin}, never the repository, so the item is listed in the bin.
 */
@Getter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

	/** The SQL condition every soft-deletable entity is filtered by. */
	public static final String NOT_DELETED = "deleted_at IS NULL";

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by")
	private Long deletedById;

	public boolean isDeleted() {
		return deletedAt != null;
	}

	/** Moves the record to the recycle bin (see RecycleBinService). */
	public void markDeleted(Instant at, Long byUserId) {
		this.deletedAt = at;
		this.deletedById = byUserId;
	}

}
