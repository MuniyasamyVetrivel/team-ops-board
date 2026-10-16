package com.teamops.module.entity;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The switch of one department tool for one department. Switching a module off keeps the row ({@code enabled} false)
 * so the switch's history stays on one record. The switch is a desired state, so there is nothing to lose by two
 * editors setting it at once; it carries no version.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "department_modules")
public class DepartmentModule extends BaseEntity {

	@Column(name = "module_key", nullable = false, length = 40, updatable = false)
	private String moduleKey;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false, updatable = false)
	private Department department;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	public DepartmentModule(String moduleKey, Department department) {
		this.moduleKey = moduleKey;
		this.department = department;
	}

}
