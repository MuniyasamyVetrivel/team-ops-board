package com.teamops.finance.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily job in the business time zone (APP_TIME_ZONE): licence renewal reminders. Idempotent; off with
 * APP_SCHEDULING_ENABLED=false.
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
@RequiredArgsConstructor
class FinanceReminderScheduler {

	private final FinanceReminderService reminders;

	@Scheduled(cron = "${app.finance.reminder-cron:0 25 8 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
	public void run() {
		int sent = reminders.sendReminders();
		if (sent > 0) {
			log.info("Sent {} licence renewal reminders", sent);
		}
	}

}
