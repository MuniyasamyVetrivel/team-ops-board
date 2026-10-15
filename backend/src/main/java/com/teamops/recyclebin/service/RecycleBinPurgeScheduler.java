package com.teamops.recyclebin.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily job in the business time zone: permanently deletes recycle bin items older than the retention setting
 * (Admin Settings, default 90 days). Each item goes in its own transaction; an item something still depends on is
 * kept and tried again the next day. Off with APP_SCHEDULING_ENABLED=false.
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
@RequiredArgsConstructor
class RecycleBinPurgeScheduler {

	private final RecycleBinService recycleBin;

	@Scheduled(cron = "${app.recycle-bin.purge-cron:0 30 2 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
	public void run() {
		int purged = recycleBin.purgeExpired(id -> {
			try {
				return recycleBin.purgeExpiredItem(id);
			}
			catch (RuntimeException ex) {
				log.warn("Could not permanently delete recycle bin item {}: {}", id, ex.getMessage());
				return false;
			}
		});
		if (purged > 0) {
			log.info("Permanently deleted {} recycle bin items past the retention period", purged);
		}
	}

}
