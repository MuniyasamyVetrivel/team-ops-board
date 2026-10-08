package com.teamops.document.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.teamops.common.storage.StoredFile;
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
@Table(name = "document_versions")
public class DocumentVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false)
	private Document document;

	@Column(name = "version_no", nullable = false)
	private int versionNo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id", nullable = false)
	private StoredFile file;

	@Column(name = "change_note", length = 500)
	private String changeNote;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uploaded_by")
	private User uploadedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

}
