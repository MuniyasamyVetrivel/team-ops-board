package com.teamops.backup.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Where finished backups are kept: a local folder now ({@link LocalBackupTarget}), Amazon S3 or Lightsail object
 * storage at deployment ({@link S3BackupTarget}), chosen with BACKUP_TARGET. Objects are addressed by file name only.
 */
public interface BackupTarget {

	/** Shown in the backup history, e.g. {@code LOCAL D:\team-ops-backups} or {@code S3 bucket/prefix}. */
	String describe();

	/** Copies a finished local file to the target under this name, replacing any object with the same name. */
	void store(Path file, String name) throws IOException;

	InputStream open(String name) throws IOException;

	/** Missing objects are ignored. */
	void delete(String name) throws IOException;

}
