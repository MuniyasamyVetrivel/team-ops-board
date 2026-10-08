package com.teamops.marketing.activity.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily job (business time zone): creates the current period's occurrence (and task) for every active activity that
 * lacks one, then sends due-soon and overdue reminders for occurrences without a task. Idempotent; off with
 * APP_SCHEDULING_ENABLED=false.
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
@RequiredArgsConstructor
class ActivityScheduler {

	private final OccurrenceEngine engine;

	@Scheduled(cron = "${app.marketing.activity-cron:0 10 0 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
	public void run() {
		int created = engine.generateDue();
		int reminded = engine.sendTasklessReminders();
		if (created > 0 || reminded > 0) {
			log.info("Recurring marketing activities: {} occurrences created, {} reminders sent", created, reminded);
		}
	}

}
