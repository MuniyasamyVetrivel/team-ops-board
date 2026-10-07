package com.teamops.ticket.entity;

import java.time.Instant;

import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
import com.teamops.sla.entity.SlaPolicy;
import com.teamops.sla.service.SlaCalculator;
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
 * A help desk ticket. SLA due times are snapshotted at creation; the clock is paused while waiting for the requester
 * and while resolved, and the paused time pushes the effective due times back.
 */
@Getter
@Setter
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

	@Column(name = "code", nullable = false, length = 20)
	private String code;

	@Column(name = "subject", nullable = false, length = 250)
	private String subject;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requester_id")
	private User requester;

	/** The handling department. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private TicketCategory category;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "priority", nullable = false, length = 32)
	private TicketPriority priority = TicketPriority.MEDIUM;

	/** Sortable priority order (URGENT highest); read-only. */
	@Formula("case priority when 'URGENT' then 4 when 'HIGH' then 3 when 'MEDIUM' then 2 else 1 end")
	private int priorityRank;

	/** Sortable lifecycle order; read-only. */
	@Formula("case status when 'NEW' then 1 when 'OPEN' then 2 when 'IN_PROGRESS' then 3 "
			+ "when 'WAITING_FOR_REQUESTER' then 4 when 'RESOLVED' then 5 else 6 end")
	private int statusRank;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private TicketStatus status = TicketStatus.NEW;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sla_policy_id")
	private SlaPolicy slaPolicy;

	@Column(name = "sla_start_at", nullable = false)
	private Instant slaStartAt;

	@Column(name = "first_response_due_at", nullable = false)
	private Instant firstResponseDueAt;

	@Column(name = "resolution_due_at", nullable = false)
	private Instant resolutionDueAt;

	@Column(name = "sla_warning_pct", nullable = false)
	private int slaWarningPct;

	@Column(name = "first_responded_at")
	private Instant firstRespondedAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "sla_paused_at")
	private Instant slaPausedAt;

	@Column(name = "sla_paused_minutes", nullable = false)
	private int slaPausedMinutes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public boolean isRequestedBy(Long userId) {
		return requester != null && requester.getId().equals(userId);
	}

	public boolean isAssignedTo(Long userId) {
		return assignee != null && assignee.getId().equals(userId);
	}

	/** Starts a pause unless one is already running. */
	public void pauseSla(Instant now) {
		if (slaPausedAt == null) {
			slaPausedAt = now;
		}
	}

	/** Ends the current pause, adding its length to the paused total. */
	public void resumeSla(Instant now) {
		if (slaPausedAt != null) {
			slaPausedMinutes += (int) SlaCalculator.minutesBetween(slaPausedAt, now);
			slaPausedAt = null;
		}
	}

	public SlaCalculator.Clock firstResponseClock() {
		return new SlaCalculator.Clock(slaStartAt, firstResponseDueAt, slaWarningPct, slaPausedMinutes, slaPausedAt,
				firstRespondedAt);
	}

	public SlaCalculator.Clock resolutionClock() {
		return new SlaCalculator.Clock(slaStartAt, resolutionDueAt, slaWarningPct, slaPausedMinutes, slaPausedAt,
				resolvedAt);
	}

}
