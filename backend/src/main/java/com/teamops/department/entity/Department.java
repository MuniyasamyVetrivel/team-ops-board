package com.teamops.department.entity;

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

@Getter
@Setter
@Entity
@Table(name = "departments")
public class Department extends BaseEntity {

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "description", length = 500)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "manager_id")
	private User manager;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private DepartmentStatus status = DepartmentStatus.ACTIVE;

}
