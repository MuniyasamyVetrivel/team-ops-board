package com.teamops.marketing.activity.entity;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.task.entity.Task;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** One period of a recurring activity (unique per activity and period start). Kept as history once closed. */
@Getter
@Setter
@Entity
@Table(name = "marketing_activity_occurrences")
public class ActivityOccurrence extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "activity_id", nullable = false, updatable = false)
	private MarketingActivity activity;

	@Column(name = "period_start", nullable = false, updatable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false, updatable = false)
	private LocalDate periodEnd;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private OccurrenceStatus status = OccurrenceStatus.PENDING;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "task_id")
	private Task task;

	@Column(name = "completed_at")
	private Instant completedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "completed_by")
	private User completedBy;

	@Column(name = "notes", length = 1000)
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
