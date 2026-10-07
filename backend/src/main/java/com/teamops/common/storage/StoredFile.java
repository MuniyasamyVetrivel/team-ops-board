package com.teamops.common.storage;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Metadata for an uploaded file; the bytes are held by a {@link FileStorageService}. */
@Getter
@Setter
@Entity
@Table(name = "files")
public class StoredFile {

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

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

}
