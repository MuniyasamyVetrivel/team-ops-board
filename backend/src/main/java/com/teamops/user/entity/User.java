package com.teamops.user.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

	@Column(name = "email", nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName = "";

	@Column(name = "job_title", length = 150)
	private String jobTitle;

	@Column(name = "phone", length = 40)
	private String phone;

	@Column(name = "location", length = 150)
	private String location;

	@Column(name = "working_hours", length = 100)
	private String workingHours;

	/** Primary department. Secondary memberships live in department_members. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reports_to_id")
	private User reportsTo;

	@Column(name = "weekly_capacity_hours", nullable = false, precision = 5, scale = 2)
	private BigDecimal weeklyCapacityHours = new BigDecimal("40.00");

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@ManyToMany
	@JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "role_id"))
	private Set<Role> roles = new HashSet<>();

	/** Grants on top of role permissions, e.g. MARKETING_VIEW for Digital Marketing staff. */
	@ManyToMany
	@JoinTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<Permission> directPermissions = new HashSet<>();

	public String getFullName() {
		return (firstName + " " + (lastName == null ? "" : lastName)).trim();
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

}
