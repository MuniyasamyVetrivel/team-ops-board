package com.teamops.task.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
import com.teamops.project.entity.Project;
import com.teamops.tag.Tag;
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

@Getter
@Setter
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

	/** Human-readable id, e.g. TSK-000042. */
	@Column(name = "code", nullable = false, length = 20)
	private String code;

	@Column(name = "title", nullable = false, length = 250)
	private String title;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "priority", nullable = false, length = 32)
	private TaskPriority priority = TaskPriority.MEDIUM;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private TaskStatus status = TaskStatus.TODO;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Column(name = "estimated_hours", precision = 6, scale = 2)
	private BigDecimal estimatedHours;

	@Column(name = "actual_hours", precision = 6, scale = 2)
	private BigDecimal actualHours;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "source", nullable = false, length = 32)
	private TaskSource source = TaskSource.MANUAL;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@ManyToMany
	@JoinTable(name = "task_tags", joinColumns = @JoinColumn(name = "task_id"),
			inverseJoinColumns = @JoinColumn(name = "tag_id"))
	private Set<Tag> tags = new HashSet<>();

	@ManyToMany
	@JoinTable(name = "task_watchers", joinColumns = @JoinColumn(name = "task_id"),
			inverseJoinColumns = @JoinColumn(name = "user_id"))
	private Set<User> watchers = new HashSet<>();

	/** Tasks that must be finished before this one. */
	@ManyToMany
	@JoinTable(name = "task_dependencies", joinColumns = @JoinColumn(name = "task_id"),
			inverseJoinColumns = @JoinColumn(name = "depends_on_task_id"))
	private Set<Task> dependsOn = new HashSet<>();

	public boolean isAssignedTo(Long userId) {
		return assignee != null && assignee.getId().equals(userId);
	}

	public boolean isCreatedBy(Long userId) {
		return createdBy != null && createdBy.getId().equals(userId);
	}

	public boolean isWatchedBy(Long userId) {
		return watchers.stream().anyMatch(user -> user.getId().equals(userId));
	}

}
