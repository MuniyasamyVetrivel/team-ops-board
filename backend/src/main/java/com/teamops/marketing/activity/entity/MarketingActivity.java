package com.teamops.marketing.activity.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
import com.teamops.task.entity.TaskPriority;
import com.teamops.user.entity.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A recurring marketing process (brief section 35). Its occurrences are the history; edits apply to occurrences
 * generated afterwards.
 */
@Getter
@Setter
@Entity
@Table(name = "marketing_activities")
public class MarketingActivity extends BaseEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description", length = 2000)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "frequency", nullable = false, length = 16)
	private Frequency frequency;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "due_offset_days", nullable = false)
	private int dueOffsetDays;

	/** {@code null}: occurrences have no task and are completed on the activity. */
	@Column(name = "task_title_template", length = 250)
	private String taskTitleTemplate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "default_assignee_id")
	private User defaultAssignee;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "task_priority", nullable = false, length = 32)
	private TaskPriority taskPriority = TaskPriority.MEDIUM;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC, id ASC")
	private List<ActivityChecklistItem> checklist = new ArrayList<>();

	public boolean generatesTasks() {
		return taskTitleTemplate != null;
	}

	/** Who works on the occurrences: the default assignee, else the owner. */
	public User assignee() {
		return defaultAssignee != null ? defaultAssignee : owner;
	}

}
