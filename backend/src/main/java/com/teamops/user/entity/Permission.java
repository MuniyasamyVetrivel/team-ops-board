package com.teamops.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Permission catalogue entry. Codes double as Spring Security authorities, e.g. {@code TASK_EDIT}. */
@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "code", nullable = false, length = 60)
	private String code;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Column(name = "module", nullable = false, length = 40)
	private String module;

	@Column(name = "description")
	private String description;

}
