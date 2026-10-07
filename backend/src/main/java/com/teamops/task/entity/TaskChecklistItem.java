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

@Getter
@Setter
@Entity
@Table(name = "task_checklists")
public class TaskChecklistItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false)
	private Task task;

	@Column(name = "content", nullable = false, length = 500)
	private String content;

	@Column(name = "is_done", nullable = false)
	private boolean done;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "done_by")
	private User doneBy;

	@Column(name = "done_at")
	private Instant doneAt;

	@Column(name = "position", nullable = false)
	private int position;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

}
