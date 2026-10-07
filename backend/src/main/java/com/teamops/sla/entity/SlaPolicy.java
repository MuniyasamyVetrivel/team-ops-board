package com.teamops.sla.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.ticket.entity.TicketPriority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** First-response and resolution targets for one ticket priority. */
@Getter
@Setter
@Entity
@Table(name = "sla_policies")
public class SlaPolicy extends BaseEntity {

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "priority", nullable = false, length = 32)
	private TicketPriority priority;

	@Column(name = "first_response_minutes", nullable = false)
	private int firstResponseMinutes;

	@Column(name = "resolution_minutes", nullable = false)
	private int resolutionMinutes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
