package com.teamops.bench.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.teamops.celebration.entity.Employee;
import com.teamops.common.persistence.SoftDeletableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * An employee on the consultant bench. The person's name, employee ID, designation and department live on their
 * Employee record (Phase X4: details are kept once); this record adds what Bench Management needs. Skills and
 * certifications are children: they stay untouched while the consultant is in the recycle bin.
 */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "bench_consultants")
public class Consultant extends SoftDeletableEntity {

	/** Written through this column, so saving the consultant never clears it while the employee is in the bin. */
	@Column(name = "employee_id", nullable = false)
	private Long employeeId;

	/**
	 * Read-only view of {@link #employeeId}: null while the employee is in the recycle bin. {@code @NotFound} loads
	 * the link eagerly.
	 */
	@ManyToOne(fetch = FetchType.EAGER)
	@NotFound(action = NotFoundAction.IGNORE)
	@JoinColumn(name = "employee_id", insertable = false, updatable = false)
	private Employee employee;

	@Column(name = "experience_years", precision = 4, scale = 1)
	private BigDecimal experienceYears;

	@Column(name = "location", length = 150)
	private String location;

	@Column(name = "available_from")
	private LocalDate availableFrom;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "employment_status", nullable = false, length = 32)
	private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@OneToMany(mappedBy = "consultant", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC, id ASC")
	private List<ConsultantSkill> skills = new ArrayList<>();

	@OneToMany(mappedBy = "consultant", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC, id ASC")
	private List<ConsultantCertification> certifications = new ArrayList<>();

}
