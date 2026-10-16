package com.teamops.backup.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * The nightly backup (BACKUP_CRON, default 02:00 in the business time zone). Off with BACKUP_ENABLED=false or
 * APP_SCHEDULING_ENABLED=false; "Run backup now" works either way.
 */
@Component
@ConditionalOnBooleanProperty(name = { "app.scheduling.enabled", "app.backup.enabled" }, matchIfMissing = true)
@RequiredArgsConstructor
class BackupScheduler {

	private final BackupService backups;

	@Scheduled(cron = "${app.backup.cron:0 0 2 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
	public void run() {
		backups.runScheduled();
	}

}
