package com.teamops.tag;

import com.teamops.common.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Shared free-form label (tasks now, knowledge articles later). Names are unique, case-insensitively. */
@Getter
@Setter
@Entity
@Table(name = "tags")
public class Tag extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 60)
	private String name;

	@Column(name = "color", length = 20)
	private String color;

}
