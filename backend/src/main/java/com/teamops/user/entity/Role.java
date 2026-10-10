package com.teamops.user.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** One of {@link RoleCodes}. */
	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description")
	private String description;

	/** Changing the permission set bumps the version too, so two admins cannot overwrite each other's matrix edits. */
	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;

	@ManyToMany
	@JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<Permission> permissions = new HashSet<>();

}
