package com.teamops.marketing.target.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.marketing.common.LeadSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A kind of monthly marketing target (brief section 32). Unit and actual source are fixed once the type has targets,
 * so past months never change meaning.
 */
@Getter
@Setter
@Entity
@Table(name = "marketing_target_types")
public class TargetType extends BaseEntity {

	@Column(name = "code", nullable = false, length = 50)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "unit", nullable = false, length = 16)
	private TargetUnit unit = TargetUnit.COUNT;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "actual_source", nullable = false, length = 32)
	private ActualSource actualSource = ActualSource.MANUAL;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "lead_source_filter", length = 32)
	private LeadSource leadSourceFilter;

	/** Overrides the global behind threshold when set. */
	@Column(name = "behind_threshold_pct", precision = 5, scale = 2)
	private BigDecimal behindThresholdPct;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "position", nullable = false)
	private int position;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
