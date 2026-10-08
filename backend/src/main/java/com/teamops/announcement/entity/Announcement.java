package com.teamops.announcement.entity;

import java.time.Instant;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** An announcement for one department, or for everyone when the target is {@code null}. */
@Getter
@Setter
@Entity
@Table(name = "announcements")
public class Announcement extends BaseEntity {

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "body", nullable = false, columnDefinition = "text")
	private String body;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_department_id")
	private Department targetDepartment;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "priority", nullable = false, length = 32)
	private AnnouncementPriority priority = AnnouncementPriority.NORMAL;

	@Column(name = "publish_at", nullable = false)
	private Instant publishAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "ack_required", nullable = false)
	private boolean ackRequired;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	/** SCHEDULED before publishAt, EXPIRED from expiresAt, otherwise ACTIVE. */
	public AnnouncementState stateAt(Instant now) {
		if (publishAt.isAfter(now)) {
			return AnnouncementState.SCHEDULED;
		}
		return expiresAt != null && !expiresAt.isAfter(now) ? AnnouncementState.EXPIRED : AnnouncementState.ACTIVE;
	}

	public boolean isCreatedBy(Long userId) {
		return createdBy != null && createdBy.getId().equals(userId);
	}

}
