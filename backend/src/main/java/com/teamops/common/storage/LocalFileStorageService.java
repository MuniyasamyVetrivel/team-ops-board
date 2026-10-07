package com.teamops.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Stores files under FILE_STORAGE_DIR as {@code yyyy/MM/<uuid>.<ext>}. Keys are generated here, never taken from
 * the client, and every resolved path is checked to stay inside the root directory.
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

	public static final String PROVIDER = "LOCAL";

	private final Path root;

	public LocalFileStorageService(StorageProperties properties) {
		this.root = Path.of(properties.dir()).toAbsolutePath().normalize();
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public String put(InputStream content, String extension) throws IOException {
		LocalDate today = LocalDate.now();
		String key = "%d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);
		Path target = resolve(key);
		Files.createDirectories(target.getParent());
		Files.copy(content, target);
		return key;
	}

	@Override
	public Resource get(String storageKey) {
		return new FileSystemResource(resolve(storageKey));
	}

	@Override
	public void delete(String storageKey) {
		try {
			Files.deleteIfExists(resolve(storageKey));
		}
		catch (IOException ex) {
			log.warn("Could not delete stored file {}", storageKey, ex);
		}
	}

	Path resolve(String storageKey) {
		Path path = root.resolve(storageKey).normalize();
		if (!path.startsWith(root)) {
			throw new UncheckedIOException(new IOException("Storage key escapes the storage root: " + storageKey));
		}
		return path;
	}

}
