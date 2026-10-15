package com.teamops.recyclebin.entity;

import java.time.Instant;

import com.teamops.common.audit.AuditIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A deleted record waiting in the recycle bin: its type and id, its name and module, and the department it belonged
 * to (for Department Managers). An index over the soft-deleted rows, removed when the record is restored or deleted
 * for good; the record's own audit history tells the story, so this row is not audited itself.
 */
@Getter
@NoArgsConstructor
@Entity
@AuditIgnore
@Table(name = "recycle_bin_items")
public class RecycleBinItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "entity_type", nullable = false, length = 60)
	private String entityType;

	@Column(name = "entity_id", nullable = false)
	private Long entityId;

	@Column(name = "label", length = 255)
	private String label;

	@Column(name = "module", nullable = false, length = 40)
	private String module;

	@Column(name = "department_id")
	private Long departmentId;

	@Column(name = "deleted_at", nullable = false)
	private Instant deletedAt;

	@Column(name = "deleted_by")
	private Long deletedById;

	public RecycleBinItem(String entityType, Long entityId, String label, String module, Long departmentId,
			Instant deletedAt, Long deletedById) {
		this.entityType = entityType;
		this.entityId = entityId;
		this.label = label;
		this.module = module;
		this.departmentId = departmentId;
		this.deletedAt = deletedAt;
		this.deletedById = deletedById;
	}

}
