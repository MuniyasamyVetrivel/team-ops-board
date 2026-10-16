package com.teamops.backup.service;

import java.nio.file.Path;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import com.teamops.backup.service.BackupProperties.TargetType;

/**
 * The backup targets: the first (BACKUP_TARGET) and an optional second copy (BACKUP_SECONDARY_TARGET), each a local
 * folder or an S3-compatible bucket. Created on first use and kept, so a bad setting (for example a missing S3 bucket
 * name) shows up as a failed copy on the status page instead of stopping the app; it is tried again on the next run.
 */
@Component
public class BackupTargets implements DisposableBean {

	/** Which copy of a backup: the first target or the second. */
	public enum Copy {

		PRIMARY, SECONDARY

	}

	private final BackupProperties properties;

	private BackupTarget primary;

	private BackupTarget secondary;

	public BackupTargets(BackupProperties properties) {
		this.properties = properties;
	}

	/** Fixed targets, for tests. */
	static BackupTargets of(BackupProperties properties, BackupTarget primary, BackupTarget secondary) {
		BackupTargets targets = new BackupTargets(properties);
		targets.primary = primary;
		targets.secondary = secondary;
		return targets;
	}

	public boolean hasSecondary() {
		return properties.secondary().enabled() || secondary != null;
	}

	public synchronized BackupTarget get(Copy copy) {
		if (copy == Copy.PRIMARY) {
			if (primary == null) {
				primary = create(properties.target(), properties.local(), properties.s3());
			}
			return primary;
		}
		if (!hasSecondary()) {
			throw new IllegalStateException("No second backup target is configured");
		}
		if (secondary == null) {
			BackupProperties.Secondary settings = properties.secondary();
			if (settings.target() == TargetType.LOCAL
					&& (settings.local() == null || BackupProperties.blank(settings.local().path()))) {
				throw new IllegalStateException("BACKUP_SECONDARY_TARGET=LOCAL needs BACKUP_SECONDARY_LOCAL_PATH");
			}
			secondary = create(settings.target(), settings.local(), settings.s3());
		}
		return secondary;
	}

	/** The target's description for the history, or what is wrong with its settings. */
	public String describe(Copy copy) {
		try {
			return get(copy).describe();
		}
		catch (RuntimeException ex) {
			TargetType type = copy == Copy.PRIMARY ? properties.target() : properties.secondary().target();
			return type + " (not available: " + BackupService.rootMessage(ex) + ")";
		}
	}

	private static BackupTarget create(TargetType type, BackupProperties.Local local, BackupProperties.S3 s3) {
		return switch (type) {
			case LOCAL -> new LocalBackupTarget(Path.of(local.path()));
			case S3 -> new S3BackupTarget(s3);
		};
	}

	@Override
	public synchronized void destroy() {
		for (BackupTarget target : new BackupTarget[] { primary, secondary }) {
			if (target instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				}
				catch (Exception ignored) {
					// shutting down
				}
			}
		}
	}

}
