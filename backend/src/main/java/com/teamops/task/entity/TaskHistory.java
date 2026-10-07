package com.teamops.task.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One field change on a task (status, assignee, due date, ...). Values are display strings. */
@Getter
@Setter
@Entity
@Table(name = "task_history")
public class TaskHistory {

	public static final int MAX_VALUE_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false)
	private Task task;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "changed_by")
	private User changedBy;

	@Column(name = "field_name", nullable = false, length = 60)
	private String fieldName;

	@Column(name = "old_value", length = 500)
	private String oldValue;

	@Column(name = "new_value", length = 500)
	private String newValue;

	@CreationTimestamp
	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

}
