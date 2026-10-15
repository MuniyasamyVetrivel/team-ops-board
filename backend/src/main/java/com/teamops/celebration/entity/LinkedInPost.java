package com.teamops.celebration.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.common.audit.AuditRef;
import com.teamops.common.audit.AuditType;
import com.teamops.common.audit.AuditedChild;
import com.teamops.common.persistence.BaseEntity;
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

/**
 * LinkedIn post tracking for one occurrence: a birthday or anniversary (subject = employee), an important day or an
 * event on {@code occurrenceDate}. Without a row, the occurrence uses {@link OccasionKind#postNeededByDefault()}.
 */
@Getter
@Setter
@Entity
@AuditType("LINKEDIN_POST")
@Table(name = "linkedin_posts")
public class LinkedInPost extends BaseEntity implements AuditedChild {

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "subject_type", nullable = false, length = 32)
	private OccasionKind subjectType;

	@Column(name = "subject_id", nullable = false)
	private Long subjectId;

	@Column(name = "occurrence_date", nullable = false)
	private LocalDate occurrenceDate;

	@Column(name = "post_needed", nullable = false)
	private boolean postNeeded;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private LinkedInPostStatus status = LinkedInPostStatus.NOT_STARTED;

	@Column(name = "post_date")
	private LocalDate postDate;

	@Column(name = "post_link", length = 500)
	private String postLink;

	/**
	 * Who last changed the post, as a user. The column is the audit stamp, written on every update; setting this only
	 * keeps the loaded entity in step.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "updated_by", insertable = false, updatable = false)
	private User updatedBy;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@Override
	public AuditRef auditParent() {
		return OccasionSubjects.of(subjectType, subjectId);
	}

}
