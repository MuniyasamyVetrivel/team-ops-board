package com.teamops.celebration.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily job in the business time zone (APP_TIME_ZONE): birthday, work anniversary, important day and event
 * reminders. Idempotent; off with APP_SCHEDULING_ENABLED=false.
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
@RequiredArgsConstructor
class CelebrationReminderScheduler {

	private final CelebrationReminderService reminders;

	@Scheduled(cron = "${app.celebrations.reminder-cron:0 0 8 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
	public void run() {
		int sent = reminders.sendReminders();
		if (sent > 0) {
			log.info("Sent {} celebration and event reminders", sent);
		}
	}

}
