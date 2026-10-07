package com.teamops.common.storage;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * Where file bytes live. Metadata lives in the {@code files} table ({@link StoredFile}). The local-disk implementation
 * is used now; S3 / SharePoint / OneDrive implementations can be added later without touching callers.
 */
public interface FileStorageService {

	/** Identifier stored in {@code files.storage_provider}. */
	String provider();

	/** Stores the bytes and returns the new storage key. */
	String put(InputStream content, String extension) throws IOException;

	Resource get(String storageKey);

	/** Best effort: missing files are ignored. */
	void delete(String storageKey);

}
