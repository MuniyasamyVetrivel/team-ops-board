package com.teamops.common.storage;

import com.teamops.common.audit.AuditRef;
import com.teamops.common.audit.AuditType;
import com.teamops.common.audit.AuditedChild;
import com.teamops.common.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * Metadata for an uploaded file; the bytes are held by a {@link FileStorageService}. A file stored or deleted for a
 * record that must not show its files to everyone (an expense's receipt) names that record as its audit owner, so the
 * file's audit entries belong to it and are hidden with it.
 */
@Getter
@Setter
@Entity
@AuditType("FILE")
@Table(name = "files")
public class StoredFile extends AuditedEntity implements AuditedChild {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "storage_provider", nullable = false, length = 32)
	private String storageProvider;

	@Column(name = "storage_key", nullable = false, length = 500)
	private String storageKey;

	@Column(name = "original_name", nullable = false)
	private String originalName;

	/** Derived from the validated extension, never from the client's header. */
	@Column(name = "content_type", nullable = false, length = 150)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "checksum_sha256", length = 64)
	private String checksumSha256;

	@Column(name = "uploaded_by")
	private Long uploadedBy;

	/** The record this file is stored or deleted for in the current change, if any; not stored. */
	@Transient
	private AuditRef auditOwner;

	@Override
	public AuditRef auditParent() {
		return auditOwner;
	}

}
