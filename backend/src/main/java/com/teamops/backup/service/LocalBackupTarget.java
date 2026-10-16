package com.teamops.backup.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * A folder on this machine (BACKUP_LOCAL_PATH), for development or as a second copy. Files are written under a
 * temporary name and moved into place, so a half-written backup never carries a real name.
 */
public class LocalBackupTarget implements BackupTarget {

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");

	private final Path root;

	public LocalBackupTarget(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	@Override
	public String describe() {
		return "LOCAL " + root;
	}

	@Override
	public void store(Path file, String name) throws IOException {
		Path target = resolve(name);
		Files.createDirectories(root);
		Path partial = root.resolve(name + ".partial");
		Files.copy(file, partial, StandardCopyOption.REPLACE_EXISTING);
		try {
			Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException ex) {
			Files.deleteIfExists(partial);
			throw ex;
		}
	}

	@Override
	public InputStream open(String name) throws IOException {
		return Files.newInputStream(resolve(name));
	}

	@Override
	public void delete(String name) throws IOException {
		Files.deleteIfExists(resolve(name));
	}

	private Path resolve(String name) {
		if (name == null || !NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("Invalid backup file name: " + name);
		}
		return root.resolve(name);
	}

}
