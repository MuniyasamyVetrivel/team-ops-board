package com.teamops.notification.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.repository.TaskReminderRow;
import com.teamops.task.repository.TaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily "task due soon" and "task overdue" reminders (brief section 65). Each reminder carries a dedup key made of
 * type, task, due date and assignee, so re-running the job never sends a duplicate, while a new due date or a new
 * assignee gets a fresh reminder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskReminderService {

	/** Due today or tomorrow counts as "due soon". */
	static final int DUE_SOON_DAYS = 1;

	private static final int KEY_BATCH = 500;

	private final TaskRepository taskRepository;

	private final NotificationRepository notificationRepository;

	private final NotificationService notificationService;

	private final BusinessCalendar calendar;

	@Scheduled(cron = "${app.notifications.reminder-cron:0 0 8 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
	public void scheduledRun() {
		int sent = sendDueReminders();
		if (sent > 0) {
			log.info("Sent {} task due/overdue reminders", sent);
		}
	}

	/** Returns the number of reminders created. */
	@Transactional
	public int sendDueReminders() {
		LocalDate today = calendar.today();
		List<TaskReminderRow> candidates = taskRepository.findReminderCandidates(TaskStatus.ACTIVE,
				today.plusDays(DUE_SOON_DAYS));
		Set<String> existing = existingKeys(candidates.stream().map(row -> dedupKey(row, today)).toList());
		int sent = 0;
		for (TaskReminderRow row : candidates) {
			String key = dedupKey(row, today);
			if (!existing.add(key)) {
				continue;
			}
			boolean overdue = row.getDueDate().isBefore(today);
			notificationService.notify(row.getAssigneeId(),
					overdue ? NotificationType.TASK_OVERDUE : NotificationType.TASK_DUE_SOON,
					row.getCode() + (overdue ? " is overdue" : row.getDueDate().isEqual(today) ? " is due today"
							: " is due tomorrow"),
					row.getTitle(), NotificationService.ENTITY_TASK, row.getId(), key);
			sent++;
		}
		return sent;
	}

	static NotificationType typeFor(LocalDate dueDate, LocalDate today) {
		return dueDate.isBefore(today) ? NotificationType.TASK_OVERDUE : NotificationType.TASK_DUE_SOON;
	}

	static String dedupKey(TaskReminderRow row, LocalDate today) {
		return typeFor(row.getDueDate(), today) + ":" + row.getId() + ":" + row.getDueDate() + ":"
				+ row.getAssigneeId();
	}

	private Set<String> existingKeys(List<String> keys) {
		Set<String> existing = new HashSet<>();
		List<String> batch = new ArrayList<>(KEY_BATCH);
		for (String key : keys) {
			batch.add(key);
			if (batch.size() == KEY_BATCH) {
				existing.addAll(notificationRepository.findExistingDedupKeys(batch));
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			existing.addAll(notificationRepository.findExistingDedupKeys(batch));
		}
		return existing;
	}

}
