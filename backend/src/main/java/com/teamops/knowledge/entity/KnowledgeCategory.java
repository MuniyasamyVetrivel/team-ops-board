package com.teamops.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Seeded categories (brief section 16): SOP, HR, IT, Security, Development, Marketing, Operations, FAQ, ... */
@Getter
@Setter
@Entity
@Table(name = "knowledge_categories")
public class KnowledgeCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "slug", nullable = false, length = 100)
	private String slug;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "position", nullable = false)
	private int position;

}
