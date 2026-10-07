package com.teamops.ticket.entity;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ticket_categories")
public class TicketCategory extends BaseEntity {

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	/** The team that handles this category; {@code null} means the requester chooses. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "default_department_id")
	private Department defaultDepartment;

	@Column(name = "active", nullable = false)
	private boolean active = true;

}
