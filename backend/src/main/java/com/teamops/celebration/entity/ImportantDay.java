package com.teamops.celebration.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
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
@Table(name = "important_days")
public class ImportantDay extends BaseEntity {

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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public boolean isYearly() {
		return recurrence == DayRecurrence.YEARLY;
	}

}
