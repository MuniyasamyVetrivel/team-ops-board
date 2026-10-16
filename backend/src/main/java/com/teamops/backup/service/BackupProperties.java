package com.teamops.backup.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.backup.*} (BACKUP_* in backend/.env). The encryption key is required for a backup to run; without it each
 * run is recorded as failed, so the status page and health check show the problem.
 *
 * @param enabled nightly schedule on or off (the "Run backup now" button works either way)
 * @param cron nightly schedule (Spring cron, evaluated in APP_TIME_ZONE)
 * @param encryptionKey base64 of 32 random bytes (AES-256)
 * @param mysqldumpPath the mysqldump executable
 * @param target where finished backups are kept: LOCAL or S3
 * @param keepDaily newest backup of each of this many most recent days is kept
 * @param keepMonthly newest backup of each of this many most recent months is kept
 * @param maxAgeHours the health check flags a problem when the last successful backup is older than this
 * @param workDir where a backup is assembled before it is copied to the target (blank: the system temp folder)
 */
@ConfigurationProperties(prefix = "app.backup")
public record BackupProperties(boolean enabled, String cron, String encryptionKey, String mysqldumpPath,
		TargetType target, Local local, S3 s3, int keepDaily, int keepMonthly, int maxAgeHours, String workDir) {

	public enum TargetType {

		LOCAL, S3

	}

	/** @param path folder outside the project and the app server's data, e.g. D:\team-ops-backups */
	public record Local(String path) {

	}

	/**
	 * Amazon S3 or an S3-compatible store such as Lightsail object storage.
	 *
	 * @param endpoint blank for AWS S3; e.g. https://s3.ap-south-1.amazonaws.com for Lightsail buckets
	 * @param accessKey blank to use the default AWS credential chain (instance role, environment)
	 */
	public record S3(String bucket, String prefix, String region, String endpoint, String accessKey,
			String secretKey, boolean pathStyle) {

	}

	public BackupProperties {
		cron = blank(cron) ? "0 0 2 * * *" : cron;
		mysqldumpPath = blank(mysqldumpPath) ? "mysqldump" : mysqldumpPath;
		target = target == null ? TargetType.LOCAL : target;
		local = local == null || blank(local.path()) ? new Local("../team-ops-backups") : local;
		s3 = s3 == null ? new S3(null, null, null, null, null, null, false) : s3;
		keepDaily = keepDaily <= 0 ? 30 : keepDaily;
		keepMonthly = keepMonthly <= 0 ? 12 : keepMonthly;
		maxAgeHours = maxAgeHours <= 0 ? 26 : maxAgeHours;
	}

	static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
