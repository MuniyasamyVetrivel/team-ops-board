package com.teamops.project.entity;

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
import lombok.Getter;
import lombok.Setter;

/** A project risk. Its severity (probability × impact) is computed, never stored. */
@Getter
@Setter
@Entity
@Table(name = "project_risks")
public class ProjectRisk extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "probability", nullable = false, length = 32)
	private RiskLevel probability = RiskLevel.MEDIUM;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "impact", nullable = false, length = 32)
	private RiskLevel impact = RiskLevel.MEDIUM;

	@Column(name = "mitigation", columnDefinition = "text")
	private String mitigation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private RiskStatus status = RiskStatus.OPEN;

}
