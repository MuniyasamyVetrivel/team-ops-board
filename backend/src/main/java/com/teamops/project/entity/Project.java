package com.teamops.project.entity;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
import com.teamops.user.entity.User;

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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A project. Progress is never stored: it is {@code progressOverride} when set, otherwise computed from the
 * project's tasks. Members and dependencies are plain link tables.
 */
@Getter
@Setter
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

	@Column(name = "code", nullable = false, length = 20)
	private String code;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private ProjectStatus status = ProjectStatus.PLANNING;

	/** Manual progress (0–100) that replaces the task-based figure; {@code null} = computed. */
	@Column(name = "progress_override")
	private Integer progressOverride;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@ManyToMany
	@JoinTable(name = "project_members", joinColumns = @JoinColumn(name = "project_id"),
			inverseJoinColumns = @JoinColumn(name = "user_id"))
	private Set<User> members = new HashSet<>();

	/** Projects that must finish before this one. */
	@ManyToMany
	@JoinTable(name = "project_dependencies", joinColumns = @JoinColumn(name = "project_id"),
			inverseJoinColumns = @JoinColumn(name = "depends_on_project_id"))
	private Set<Project> dependsOn = new HashSet<>();

	public boolean isOwnedBy(Long userId) {
		return owner != null && owner.getId().equals(userId);
	}

	public boolean hasMember(Long userId) {
		return members.stream().anyMatch(user -> user.getId().equals(userId));
	}

}
