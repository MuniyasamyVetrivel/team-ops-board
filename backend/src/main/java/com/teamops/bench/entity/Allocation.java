package com.teamops.bench.entity;

import java.time.LocalDate;

import org.hibernate.annotations.SQLRestriction;

import com.teamops.common.persistence.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A consultant allocated to a client engagement. A live allocation's consultant and engagement are always live: they
 * cannot be deleted while it exists, and it comes back from the recycle bin only while both are live.
 */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "bench_allocations")
public class Allocation extends SoftDeletableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "consultant_id", nullable = false)
	private Consultant consultant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "engagement_id", nullable = false)
	private ClientEngagement engagement;

	@Column(name = "role", nullable = false, length = 150)
	private String role;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Open-ended when null. */
	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "allocation_percent", nullable = false)
	private int allocationPercent;

	@Column(name = "billable", nullable = false)
	private boolean billable = true;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
