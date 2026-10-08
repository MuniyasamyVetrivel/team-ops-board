package com.teamops.marketing.activity.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.common.web.ClientInfo;
import com.teamops.marketing.activity.entity.ActivityChecklistItem;
import com.teamops.marketing.activity.entity.ActivityOccurrence;
import com.teamops.marketing.activity.entity.MarketingActivity;
import com.teamops.marketing.activity.entity.OccurrenceStatus;
import com.teamops.marketing.activity.repository.ActivityOccurrenceRepository;
import com.teamops.marketing.activity.repository.MarketingActivityRepository;
import com.teamops.marketing.activity.service.Recurrence.Period;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskChecklistItem;
import com.teamops.task.entity.TaskHistory;
import com.teamops.task.entity.TaskSource;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.event.TaskStatusChangedEvent;
import com.teamops.task.repository.TaskChecklistItemRepository;
import com.teamops.task.repository.TaskHistoryRepository;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Generates and closes occurrences of recurring activities (brief sections 35–36). Closing an occurrence (completed or
 * skipped) creates the next one with its task, in the same transaction; generation is idempotent through the unique
 * key on activity and period, so the daily job and a completion can never create a period twice. An occurrence with a
 * task follows its task's status.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OccurrenceEngine {

	static final Set<OccurrenceStatus> OPEN = EnumSet.of(OccurrenceStatus.PENDING, OccurrenceStatus.IN_PROGRESS);

	/** Taskless occurrences due within this many days get a reminder. */
	static final int DUE_SOON_DAYS = 1;

	private static final int TITLE_MAX = 250;

	private final MarketingActivityRepository activityRepository;

	private final ActivityOccurrenceRepository occurrenceRepository;

	private final TaskRepository taskRepository;

	private final TaskChecklistItemRepository checklistRepository;

	private final TaskHistoryRepository historyRepository;

	private final UserRepository userRepository;

	private final CodeGenerator codeGenerator;

	private final NotificationService notificationService;

	private final NotificationRepository notificationRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	/**
	 * Makes sure the occurrence for the current period exists for every active activity that covers it (the daily job).
	 *
	 * @return the number of occurrences created
	 */
	public int generateDue() {
		LocalDate today = calendar.today();
		int created = 0;
		for (MarketingActivity activity : activityRepository.findByActiveTrue()) {
			if (ensureCurrent(activity, today).created()) {
				created++;
			}
		}
		return created;
	}

	/** The current period's occurrence, created when missing and the activity is active and covers the period. */
	public Ensured ensureCurrent(MarketingActivity activity, LocalDate today) {
		Period period = Recurrence.containing(activity.getFrequency(), today);
		if (!activity.isActive() || !Recurrence.covers(period, activity.getStartDate(), activity.getEndDate())) {
			return new Ensured(null, false);
		}
		return ensure(activity, period);
	}

	/** The occurrence for {@code period}: the existing one, or a new one with its task (when the activity has tasks). */
	public Ensured ensure(MarketingActivity activity, Period period) {
		Optional<ActivityOccurrence> existing = occurrenceRepository.findByActivityIdAndPeriodStart(activity.getId(),
				period.start());
		if (existing.isPresent()) {
			return new Ensured(existing.get(), false);
		}
		ActivityOccurrence occurrence = new ActivityOccurrence();
		occurrence.setActivity(activity);
		occurrence.setPeriodStart(period.start());
		occurrence.setPeriodEnd(period.end());
		occurrence.setDueDate(Recurrence.dueDate(period, activity.getDueOffsetDays()));
		if (activity.generatesTasks()) {
			occurrence.setTask(createTask(activity, period, occurrence.getDueDate()));
		}
		ActivityOccurrence saved = occurrenceRepository.save(occurrence);
		notifyNew(saved);
		return new Ensured(saved, true);
	}

	/**
	 * Closes an open occurrence as COMPLETED or SKIPPED, then creates the next period's occurrence while the activity
	 * is active and still running. Past occurrences stay as history.
	 */
	public void close(ActivityOccurrence occurrence, OccurrenceStatus status, Long userId) {
		occurrence.setStatus(status);
		occurrence.setCompletedAt(status == OccurrenceStatus.COMPLETED ? calendar.now() : null);
		occurrence.setCompletedBy(userId == null ? null : userRepository.getReferenceById(userId));
		occurrenceRepository.flush();
		MarketingActivity activity = occurrence.getActivity();
		Period next = Recurrence.next(activity.getFrequency(),
				new Period(occurrence.getPeriodStart(), occurrence.getPeriodEnd()));
		if (activity.isActive() && Recurrence.covers(next, activity.getStartDate(), activity.getEndDate())) {
			ensure(activity, next);
		}
	}

	/** Back to open (a reopened task, or "reopen" on a taskless occurrence). The next occurrence is kept. */
	public void reopen(ActivityOccurrence occurrence, OccurrenceStatus status) {
		occurrence.setStatus(status);
		occurrence.setCompletedAt(null);
		occurrence.setCompletedBy(null);
	}

	/**
	 * Keeps an occurrence in step with its task: completed completes it (and creates the next), cancelled skips it
	 * (and creates the next), any active status reopens or progresses it.
	 */
	@EventListener
	public void onTaskStatusChanged(TaskStatusChangedEvent event) {
		occurrenceRepository.findByTaskId(event.taskId()).ifPresent(occurrence -> {
			TaskStatus status = event.status();
			if (status == TaskStatus.COMPLETED) {
				close(occurrence, OccurrenceStatus.COMPLETED, event.actorId());
			}
			else if (status == TaskStatus.CANCELLED) {
				close(occurrence, OccurrenceStatus.SKIPPED, event.actorId());
			}
			else {
				reopen(occurrence, status == TaskStatus.TODO ? OccurrenceStatus.PENDING : OccurrenceStatus.IN_PROGRESS);
			}
		});
	}

	/**
	 * Due-soon and overdue reminders for occurrences without a task (generated tasks get the task reminders). One
	 * reminder per occurrence, kind and due date.
	 *
	 * @return the number of reminders created
	 */
	public int sendTasklessReminders() {
		LocalDate today = calendar.today();
		int sent = 0;
		for (ActivityOccurrence occurrence : occurrenceRepository.findTasklessDue(OPEN, today.plusDays(DUE_SOON_DAYS))) {
			User recipient = occurrence.getActivity().assignee();
			if (recipient == null) {
				continue;
			}
			boolean overdue = occurrence.getDueDate().isBefore(today);
			String key = (overdue ? "ACTIVITY_OVERDUE:" : "ACTIVITY_DUE:") + occurrence.getId() + ":"
					+ occurrence.getDueDate() + ":" + recipient.getId();
			if (!notificationRepository.findExistingDedupKeys(List.of(key)).isEmpty()) {
				continue;
			}
			String label = Recurrence.label(occurrence.getActivity().getFrequency(),
					new Period(occurrence.getPeriodStart(), occurrence.getPeriodEnd()));
			notificationService.notify(recipient.getId(),
					// The generic reminder types: the bell already knows them (dedicated types can come with the UI).
					overdue ? NotificationType.TASK_OVERDUE : NotificationType.TASK_DUE_SOON,
					occurrence.getActivity().getName() + (overdue ? " is overdue" : " is due " + dueWords(occurrence.getDueDate(), today)),
					label, NotificationService.ENTITY_MARKETING_ACTIVITY, occurrence.getActivity().getId(), key);
			sent++;
		}
		return sent;
	}

	/** Whether {@link #ensure} created the occurrence or found it. */
	public record Ensured(ActivityOccurrence occurrence, boolean created) {
	}

	private Task createTask(MarketingActivity activity, Period period, LocalDate dueDate) {
		Task task = new Task();
		task.setCode(codeGenerator.next(CodeGenerator.TASK));
		String title = Recurrence.title(activity.getTaskTitleTemplate(), activity.getFrequency(), period);
		task.setTitle(title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title);
		String generated = "Generated by the recurring activity \"" + activity.getName() + "\" for "
				+ Recurrence.label(activity.getFrequency(), period) + ".";
		task.setDescription(activity.getDescription() == null ? generated : activity.getDescription() + "\n\n" + generated);
		task.setDepartment(activity.getDepartment());
		task.setAssignee(activity.assignee());
		task.setCreatedBy(activity.getOwner());
		task.setPriority(activity.getTaskPriority());
		task.setStartDate(period.start());
		task.setDueDate(dueDate);
		task.setSource(TaskSource.MARKETING_ACTIVITY);
		Task saved = taskRepository.save(task);
		int position = 0;
		for (ActivityChecklistItem item : activity.getChecklist()) {
			TaskChecklistItem copy = new TaskChecklistItem();
			copy.setTask(saved);
			copy.setContent(item.getContent());
			copy.setPosition(position++);
			checklistRepository.save(copy);
		}
		TaskHistory history = new TaskHistory();
		history.setTask(saved);
		history.setFieldName("created");
		history.setNewValue(saved.getCode());
		historyRepository.save(history);
		Map<String, Object> details = new HashMap<>();
		details.put("code", saved.getCode());
		details.put("source", TaskSource.MARKETING_ACTIVITY);
		details.put("activityId", activity.getId());
		auditService.record(AuditAction.TASK_CREATED, null, "TASK", saved.getId(), details, ClientInfo.unknown());
		return saved;
	}

	/**
	 * Tells the assignee about a generated task. Occurrences without a task are announced by the due-soon reminder
	 * instead ({@link #sendTasklessReminders}).
	 */
	private void notifyNew(ActivityOccurrence occurrence) {
		User recipient = occurrence.getActivity().assignee();
		Task task = occurrence.getTask();
		if (recipient == null || task == null) {
			return;
		}
		notificationService.notify(recipient.getId(), NotificationType.TASK_ASSIGNED, task.getCode() + " assigned to you",
				task.getTitle() + " · due " + occurrence.getDueDate(), NotificationService.ENTITY_TASK, task.getId(), null);
	}

	private static String dueWords(LocalDate due, LocalDate today) {
		return due.isEqual(today) ? "today" : "tomorrow";
	}

}
