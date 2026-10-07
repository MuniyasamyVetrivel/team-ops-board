package com.teamops.devdata;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.calendar.entity.CalendarEvent;
import com.teamops.calendar.entity.CalendarEventType;
import com.teamops.calendar.repository.CalendarEventRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.notification.service.TaskReminderService;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Development data for the dashboard (Phase 6): task activity history, calendar events and due/overdue reminders.
 * Every part is idempotent (history per task, events while the table is empty, reminders by dedup key), so restarts
 * add nothing new.
 * History timestamps come from the seeded tasks, so they are relative to the day the tasks were seeded.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevDashboardSeeder {

	private final JdbcTemplate jdbc;

	private final CalendarEventRepository eventRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final TaskReminderService reminderService;

	private final BusinessCalendar calendar;

	/** Returns a short summary of what was created. */
	@Transactional
	public String seed() {
		int history = seedHistory();
		int events = seedEvents();
		int reminders = reminderService.sendDueReminders();
		return "%d activity entries, %d calendar events, %d reminders".formatted(history, events, reminders);
	}

	/**
	 * Completions at their real completion time, and status moves spread over the last three days, for tasks that
	 * have no history yet (so this also backfills databases seeded before Phase 6, and never duplicates).
	 */
	private int seedHistory() {
		int completed = jdbc.update("""
				insert into task_history (task_id, changed_by, field_name, old_value, new_value, changed_at)
				select t.id, t.assignee_id, 'status', 'IN_REVIEW', 'COMPLETED', t.completed_at
				from tasks t
				where t.status = 'COMPLETED' and t.completed_at is not null
				  and not exists (select 1 from task_history h where h.task_id = t.id)
				""");
		int moved = jdbc.update("""
				insert into task_history (task_id, changed_by, field_name, old_value, new_value, changed_at)
				select t.id, t.assignee_id, 'status', 'TODO', t.status, timestampadd(minute, -((t.id * 37) % 4320), ?)
				from tasks t
				where t.status in ('IN_PROGRESS', 'BLOCKED', 'IN_REVIEW')
				  and not exists (select 1 from task_history h where h.task_id = t.id)
				""", Timestamp.from(calendar.now()));
		return completed + moved;
	}

	private int seedEvents() {
		if (eventRepository.count() > 0) {
			return 0;
		}
		Map<String, Department> departments = new HashMap<>();
		departmentRepository.findAll().forEach(d -> departments.put(d.getCode(), d));
		User rakesh = userRepository.findByEmailIgnoreCase("rakesh@teamops.local").orElse(null);
		LocalDate today = calendar.today();

		allDay("Quarterly town hall", CalendarEventType.TEAM_EVENT, today.plusDays(6), today.plusDays(6), null, null,
				rakesh);
		allDay("Company holiday", CalendarEventType.IMPORTANT_DATE, today.plusDays(13), today.plusDays(13), null,
				null, rakesh);
		allDay("Q4 planning deadline", CalendarEventType.IMPORTANT_DATE, today.plusDays(20), today.plusDays(20),
				null, null, rakesh);
		timed("Sprint review", CalendarEventType.MEETING, today.plusDays(2), LocalTime.of(15, 0), 60,
				departments.get("WEBDEV"), rakesh);
		timed("Content planning", CalendarEventType.MEETING, today.plusDays(1), LocalTime.of(11, 0), 45,
				departments.get("DM"), rakesh);
		timed("Security awareness session", CalendarEventType.TEAM_EVENT, today.plusDays(9), LocalTime.of(16, 0), 90,
				departments.get("CYBERSEC"), rakesh);
		leave("karthik.raj@teamops.local", today.plusDays(3), today.plusDays(4), rakesh);
		leave("priya.menon@teamops.local", today.plusDays(10), today.plusDays(12), rakesh);
		leave("vignesh.raman@teamops.local", today.minusDays(1), today, rakesh);
		return (int) eventRepository.count();
	}

	private void leave(String email, LocalDate from, LocalDate to, User createdBy) {
		userRepository.findByEmailIgnoreCase(email)
			.ifPresent(user -> allDay(user.getFullName() + " on leave", CalendarEventType.LEAVE, from, to,
					user.getDepartment(), user, createdBy));
	}

	private void allDay(String title, CalendarEventType type, LocalDate from, LocalDate to, Department department,
			User user, User createdBy) {
		save(title, type, true, calendar.startOf(from), calendar.startOf(to.plusDays(1)), department, user, createdBy);
	}

	private void timed(String title, CalendarEventType type, LocalDate day, LocalTime start, int minutes,
			Department department, User createdBy) {
		Instant startAt = day.atTime(start).atZone(calendar.zone()).toInstant();
		save(title, type, false, startAt, startAt.plusSeconds(minutes * 60L), department, null, createdBy);
	}

	private void save(String title, CalendarEventType type, boolean allDay, Instant start, Instant end,
			Department department, User user, User createdBy) {
		CalendarEvent event = new CalendarEvent();
		event.setTitle(title);
		event.setEventType(type);
		event.setAllDay(allDay);
		event.setStartAt(start);
		event.setEndAt(end);
		event.setDepartment(department);
		event.setUser(user);
		event.setCreatedBy(createdBy);
		eventRepository.save(event);
	}

}
