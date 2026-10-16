package com.teamops.vendor.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily job in the business time zone (APP_TIME_ZONE): contract renewal reminders. Idempotent; off with
 * APP_SCHEDULING_ENABLED=false.
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
@RequiredArgsConstructor
class VendorReminderScheduler {

	private final VendorReminderService reminders;

	@Scheduled(cron = "${app.vendors.reminder-cron:0 20 8 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
	public void run() {
		int sent = reminders.sendReminders();
		if (sent > 0) {
			log.info("Sent {} contract renewal reminders", sent);
		}
	}

}
