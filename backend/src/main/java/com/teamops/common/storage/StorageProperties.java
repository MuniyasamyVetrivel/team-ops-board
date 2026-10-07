package com.teamops.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.storage.*} (FILE_STORAGE_DIR, FILE_MAX_SIZE_MB). */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String dir, int maxFileSizeMb) {

	public StorageProperties {
		dir = dir == null || dir.isBlank() ? "./uploads" : dir;
		maxFileSizeMb = maxFileSizeMb <= 0 ? 20 : maxFileSizeMb;
	}

	public long maxFileSizeBytes() {
		return maxFileSizeMb * 1024L * 1024L;
	}

}
