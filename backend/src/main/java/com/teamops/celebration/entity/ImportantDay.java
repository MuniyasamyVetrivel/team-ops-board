package com.teamops.celebration.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.SoftDeletableEntity;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A marketing important day. A YEARLY day falls on the same month and day every year from {@code date}'s year on; a
 * ONE_OFF day only on {@code date}.
 */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "important_days")
public class ImportantDay extends SoftDeletableEntity {

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Column(name = "day_date", nullable = false)
	private LocalDate date;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "category", nullable = false, length = 32)
	private ImportantDayCategory category;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "region", nullable = false, length = 16)
	private Region region;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "recurrence", nullable = false, length = 16)
	private DayRecurrence recurrence;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "marketing_notes", columnDefinition = "text")
	private String marketingNotes;

	/** The creator as a user; the column is written through the audit stamp ({@link #setCreatedBy}). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", insertable = false, updatable = false)
	private User createdBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public boolean isYearly() {
		return recurrence == DayRecurrence.YEARLY;
	}

	public void setCreatedBy(User createdBy) {
		this.createdBy = createdBy;
		assignCreatedBy(createdBy == null ? null : createdBy.getId());
	}

}
