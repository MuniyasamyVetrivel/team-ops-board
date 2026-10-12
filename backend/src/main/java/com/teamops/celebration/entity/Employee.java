package com.teamops.celebration.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.common.storage.StoredFile;
import com.teamops.department.entity.Department;
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
 * Someone whose birthday and work anniversary the company celebrates. Most employees never sign in; {@code user} links
 * the ones who do. Occurrences are computed from the dates every year, never stored.
 */
@Getter
@Setter
@Entity
@Table(name = "employees")
public class Employee extends BaseEntity {

	@Column(name = "employee_code", nullable = false, length = 40)
	private String employeeCode;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "date_of_birth", nullable = false)
	private LocalDate dateOfBirth;

	@Column(name = "designation", nullable = false, length = 150)
	private String designation;

	@Column(name = "reporting_manager", nullable = false, length = 150)
	private String reportingManager;

	@Column(name = "date_of_joining")
	private LocalDate dateOfJoining;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	@Column(name = "work_email", length = 254)
	private String workEmail;

	@Column(name = "office_location", length = 150)
	private String officeLocation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "photo_file_id")
	private StoredFile photo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private EmployeeStatus status = EmployeeStatus.ACTIVE;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public String getFullName() {
		return (firstName + " " + lastName).trim();
	}

	public boolean isActive() {
		return status == EmployeeStatus.ACTIVE;
	}

}
