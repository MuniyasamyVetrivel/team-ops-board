package com.teamops.backup.service;

import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Picks the backup target from BACKUP_TARGET (LOCAL or S3). Created on first use, so a bad S3 setting shows up as a
 * failed backup on the status page instead of stopping the app.
 */
@Configuration
class BackupTargetConfig {

	@Bean
	@Lazy
	BackupTarget backupTarget(BackupProperties properties) {
		return switch (properties.target()) {
			case LOCAL -> new LocalBackupTarget(Path.of(properties.local().path()));
			case S3 -> new S3BackupTarget(properties.s3());
		};
	}

}
