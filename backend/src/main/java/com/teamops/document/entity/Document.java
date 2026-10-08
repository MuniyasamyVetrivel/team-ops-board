package com.teamops.document.entity;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.department.entity.Department;
import com.teamops.project.entity.Project;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A versioned document (brief section 17). Versions are numbered 1..n and never deleted individually, so
 * {@code currentVersionNo} is both the latest version and the version count. Bytes live behind
 * {@code FileStorageService}, so S3/SharePoint can replace local storage later.
 */
@Getter
@Setter
@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	/** {@code null} = company-wide. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uploaded_by")
	private User uploadedBy;

	@Column(name = "current_version_no", nullable = false)
	private int currentVersionNo = 1;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public boolean isUploadedBy(Long userId) {
		return uploadedBy != null && uploadedBy.getId().equals(userId);
	}

}
