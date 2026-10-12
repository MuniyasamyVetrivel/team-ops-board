package com.teamops.team.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.persistence.SpecificationAggregates;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageResponse;
import com.teamops.project.dto.ProjectRef;
import com.teamops.sla.service.SlaCalculator;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.repository.TaskRepository;
import com.teamops.task.repository.TaskSpecifications;
import com.teamops.team.dto.WorkHistoryDtos;
import com.teamops.team.dto.WorkHistoryDtos.DateField;
import com.teamops.team.dto.WorkHistoryDtos.DatePreset;
import com.teamops.team.dto.WorkHistoryDtos.TaskRow;
import com.teamops.team.dto.WorkHistoryDtos.TicketRow;
import com.teamops.team.dto.WorkHistoryDtos.WorkSummary;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.ticket.repository.TicketSpecifications;
import com.teamops.ticket.service.TicketAccess;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * A team member's full work history: the tasks and tickets assigned to them, filtered by status (including the
 * virtual "overdue"), a date range on one date field, project or category, priority and text, with a summary over the
 * same filters and a CSV export.
 * <p>
 * Who may look: a Super Admin at everyone, a department manager at people in the departments they manage, an employee
 * only at themselves ({@link AccessScopeService#canViewWorkOf}); others get 403. Within that, each task and ticket
 * still follows its module's visibility rules, so a manager never sees another department's tasks through a person.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkHistoryService {

	public static final int EXPORT_LIMIT = 10_000;

	private static final Set<TaskStatus> TASK_STATUSES = EnumSet.allOf(TaskStatus.class);

	private static final Set<TicketStatus> RESOLVED = EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

	private final UserRepository userRepository;

	private final AccessScopeService accessScopeService;

	private final TaskRepository taskRepository;

	private final TicketRepository ticketRepository;

	private final SpecificationAggregates aggregates;

	private final BusinessCalendar calendar;

	/**
	 * Filters shared by tasks and tickets. {@code groupId} is the project (tasks) or the category (tickets). A
	 * {@code preset} replaces {@code from}/{@code to}.
	 */
	public record Filter(Set<String> statuses, DateField dateField, DatePreset preset, LocalDate from, LocalDate to,
			Long groupId, Set<String> priorities, String search) {

		/** Without a field, a date range applies to the completed (resolved) date. */
		DateField field() {
			return dateField == null ? DateField.COMPLETED : dateField;
		}

		/** The filter with its preset turned into dates. */
		Filter resolve(LocalDate today) {
			if (preset == null) {
				return this;
			}
			LocalDate week = WorkHistoryRules.weekOf(today);
			LocalDate month = today.withDayOfMonth(1);
			LocalDate[] range = switch (preset) {
				case THIS_WEEK -> new LocalDate[] { week, week.plusDays(6) };
				case LAST_WEEK -> new LocalDate[] { week.minusWeeks(1), week.minusDays(1) };
				case THIS_MONTH -> new LocalDate[] { month, month.plusMonths(1).minusDays(1) };
				case LAST_MONTH -> new LocalDate[] { month.minusMonths(1), month.minusDays(1) };
				case LAST_3_MONTHS -> new LocalDate[] { today.minusMonths(3).plusDays(1), today };
				case LAST_30_DAYS -> new LocalDate[] { today.minusDays(29), today };
			};
			return new Filter(statuses, dateField, null, range[0], range[1], groupId, priorities, search);
		}

	}

	// --- tasks --------------------------------------------------------------------------------------------------

	public PageResponse<TaskRow> tasks(Long userId, Filter filter, Pageable pageable, AuthenticatedUser viewer) {
		User person = person(userId, viewer);
		LocalDate today = calendar.today();
		Specification<Task> spec = taskSpec(person, filter.resolve(today), viewer, today);
		return PageResponse.of(taskRepository.findAll(spec, pageable).map(task -> taskRow(task, today)));
	}

	public WorkSummary taskSummary(Long userId, Filter requested, AuthenticatedUser viewer) {
		User person = person(userId, viewer);
		LocalDate today = calendar.today();
		Filter filter = requested.resolve(today);
		Specification<Task> spec = taskSpec(person, filter, viewer, today);

		Map<TaskStatus, Long> counts = aggregates.countBy(Task.class, spec, "status", TaskStatus.class);
		Map<String, Long> byStatus = new LinkedHashMap<>();
		TASK_STATUSES.forEach(status -> byStatus.put(status.name(), counts.getOrDefault(status, 0L)));
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		long overdue = aggregates.count(Task.class, spec.and(TaskSpecifications.overdue(today)));
		BigDecimal hours = aggregates.sum(Task.class, spec, "actualHours");

		List<Tuple> completedRows = aggregates.select(Task.class,
				spec.and(TaskSpecifications.statusIn(Set.of(TaskStatus.COMPLETED))), "completedAt", "dueDate", "startDate",
				"createdAt");
		List<LocalDate> completions = new ArrayList<>();
		List<Long> durations = new ArrayList<>();
		long withDue = 0;
		long onTime = 0;
		for (Tuple row : completedRows) {
			Instant completedAt = row.get(0, Instant.class);
			if (completedAt == null) {
				continue;
			}
			LocalDate completed = date(completedAt);
			LocalDate due = row.get(1, LocalDate.class);
			completions.add(completed);
			durations.add(WorkHistoryRules.daysToComplete(row.get(2, LocalDate.class), date(row.get(3, Instant.class)), completed));
			if (due != null) {
				withDue++;
				onTime += Boolean.TRUE.equals(WorkHistoryRules.onTime(completed, due)) ? 1 : 0;
			}
		}
		return new WorkSummary(UserSummary.of(person), today, filter.from(), filter.to(), total, byStatus, overdue,
				completions.size(), WorkHistoryRules.percent(onTime, withDue), WorkHistoryRules.average(durations), hours,
				WorkHistoryRules.perWeek(WorkHistoryRules.weeks(filter.from(), filter.to(), today), completions));
	}

	/** The filtered list as CSV (same order as on screen), at most {@value #EXPORT_LIMIT} rows. */
	public Export exportTasks(Long userId, Filter filter, Sort sort, AuthenticatedUser viewer) {
		Pageable pageable = PageRequest.of(0, EXPORT_LIMIT, sort);
		PageResponse<TaskRow> rows = tasks(userId, filter, pageable, viewer);
		List<List<String>> lines = rows.content()
			.stream()
			.map(r -> List.of(r.code(), r.title(), r.project() == null ? "" : r.project().name(), r.priority().name(),
					r.status().name(), text(r.startDate()), text(r.dueDate()), text(r.completedDate()),
					text(r.estimatedHours()), text(r.actualHours()), timeliness(r.onTime(), r.daysLate(), r.overdue())))
			.toList();
		byte[] csv = CsvWriter.write(List.of("Task ID", "Title", "Project", "Priority", "Status", "Start date", "Due date",
				"Completed date", "Estimated hours", "Actual hours", "On time"), lines);
		return new Export(fileName(userId, "tasks"), csv);
	}

	// --- tickets ------------------------------------------------------------------------------------------------

	public PageResponse<TicketRow> tickets(Long userId, Filter filter, Pageable pageable, AuthenticatedUser viewer) {
		User person = person(userId, viewer);
		Instant now = calendar.now();
		Filter resolved = filter.resolve(calendar.today());
		return PageResponse.of(ticketRepository.findAll(ticketSpec(person, resolved, viewer, now), pageable)
			.map(ticket -> ticketRow(ticket, now)));
	}

	public WorkSummary ticketSummary(Long userId, Filter requested, AuthenticatedUser viewer) {
		User person = person(userId, viewer);
		Instant now = calendar.now();
		LocalDate today = calendar.today();
		Filter filter = requested.resolve(today);
		Specification<Ticket> spec = ticketSpec(person, filter, viewer, now);

		Map<TicketStatus, Long> counts = aggregates.countBy(Ticket.class, spec, "status", TicketStatus.class);
		Map<String, Long> byStatus = new LinkedHashMap<>();
		for (TicketStatus status : TicketStatus.values()) {
			byStatus.put(status.name(), counts.getOrDefault(status, 0L));
		}
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		long overdue = aggregates.count(Ticket.class, spec.and(TicketSpecifications.overdue(now)));

		List<Tuple> resolvedRows = aggregates.select(Ticket.class, spec.and(TicketSpecifications.statusIn(RESOLVED)),
				"resolvedAt", "createdAt", "slaStartAt", "resolutionDueAt", "slaWarningPct", "slaPausedMinutes",
				"slaPausedAt");
		List<LocalDate> completions = new ArrayList<>();
		List<Long> durations = new ArrayList<>();
		long met = 0;
		for (Tuple row : resolvedRows) {
			Instant resolvedAt = row.get(0, Instant.class);
			if (resolvedAt == null) {
				continue;
			}
			LocalDate resolved = date(resolvedAt);
			completions.add(resolved);
			durations.add(WorkHistoryRules.daysToComplete(null, date(row.get(1, Instant.class)), resolved));
			SlaCalculator.Clock clock = new SlaCalculator.Clock(row.get(2, Instant.class), row.get(3, Instant.class),
					row.get(4, Integer.class), row.get(5, Integer.class), row.get(6, Instant.class), resolvedAt);
			met += SlaCalculator.evaluate(clock, now).compliant() ? 1 : 0;
		}
		return new WorkSummary(UserSummary.of(person), today, filter.from(), filter.to(), total, byStatus, overdue,
				completions.size(), WorkHistoryRules.percent(met, completions.size()), WorkHistoryRules.average(durations), null,
				WorkHistoryRules.perWeek(WorkHistoryRules.weeks(filter.from(), filter.to(), today), completions));
	}

	public Export exportTickets(Long userId, Filter filter, Sort sort, AuthenticatedUser viewer) {
		PageResponse<TicketRow> rows = tickets(userId, filter, PageRequest.of(0, EXPORT_LIMIT, sort), viewer);
		List<List<String>> lines = rows.content()
			.stream()
			.map(r -> List.of(r.code(), r.subject(), text(r.category()), r.priority().name(), r.status().name(),
					text(r.createdDate()), text(r.dueAt() == null ? null : date(r.dueAt())), text(r.resolvedDate()),
					text(r.slaState()), timeliness(r.onTime(), r.daysLate(), r.overdue())))
			.toList();
		byte[] csv = CsvWriter.write(List.of("Ticket ID", "Subject", "Category", "Priority", "Status", "Created date",
				"Resolution due", "Resolved date", "SLA", "On time"), lines);
		return new Export(fileName(userId, "tickets"), csv);
	}

	public record Export(String fileName, byte[] content) {
	}

	// --- helpers ------------------------------------------------------------------------------------------------

	/** The person, if the viewer may see their work: 404 for nobody, 403 outside the viewer's scope. */
	private User person(Long userId, AuthenticatedUser viewer) {
		User person = userRepository.findWithDepartmentById(userId)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Team member not found"));
		if (!accessScopeService.canViewWorkOf(viewer, person.getId(), person.getDepartment().getId())) {
			throw ApiException.forbidden("WORK_NOT_VISIBLE",
					"You can see the work of people in the departments you manage, and your own");
		}
		return person;
	}

	private Specification<Task> taskSpec(User person, Filter filter, AuthenticatedUser viewer, LocalDate today) {
		requireRange(filter);
		AccessScope scope = accessScopeService.scopeFor(viewer);
		boolean overdue = false;
		Set<TaskStatus> statuses = EnumSet.noneOf(TaskStatus.class);
		for (String raw : nonNull(filter.statuses())) {
			String status = raw.trim().toUpperCase(Locale.ROOT);
			if (WorkHistoryDtos.OVERDUE.equals(status)) {
				overdue = true;
			}
			else {
				statuses.add(parse(TaskStatus.class, status, "status"));
			}
		}
		Set<TaskPriority> priorities = EnumSet.noneOf(TaskPriority.class);
		nonNull(filter.priorities()).forEach(p -> priorities.add(parse(TaskPriority.class, p, "priority")));
		Specification<Task> dates = switch (filter.field()) {
			case COMPLETED -> TaskSpecifications.instantBetween("completedAt", startOf(filter.from()), startOfNext(filter.to()));
			case CREATED -> TaskSpecifications.instantBetween("createdAt", startOf(filter.from()), startOfNext(filter.to()));
			case DUE -> TaskSpecifications.dateBetween("dueDate", filter.from(), filter.to());
			case START -> TaskSpecifications.dateBetween("startDate", filter.from(), filter.to());
		};
		return TaskSpecifications.assignee(person.getId())
			.and(TaskSpecifications.visibleTo(scope))
			.and(TaskSpecifications.statusOrOverdue(statuses, overdue, today))
			.and(TaskSpecifications.project(filter.groupId()))
			.and(TaskSpecifications.priorityIn(priorities))
			.and(TaskSpecifications.matches(filter.search()))
			.and(dates);
	}

	private Specification<Ticket> ticketSpec(User person, Filter filter, AuthenticatedUser viewer, Instant now) {
		requireRange(filter);
		TicketAccess access = new TicketAccess(viewer, accessScopeService.scopeFor(viewer));
		boolean overdue = false;
		Set<TicketStatus> statuses = EnumSet.noneOf(TicketStatus.class);
		for (String raw : nonNull(filter.statuses())) {
			String status = raw.trim().toUpperCase(Locale.ROOT);
			if (WorkHistoryDtos.OVERDUE.equals(status)) {
				overdue = true;
			}
			else {
				statuses.add(parse(TicketStatus.class, status, "status"));
			}
		}
		Set<TicketPriority> priorities = EnumSet.noneOf(TicketPriority.class);
		nonNull(filter.priorities()).forEach(p -> priorities.add(parse(TicketPriority.class, p, "priority")));
		String dateAttribute = switch (filter.field()) {
			case COMPLETED -> "resolvedAt";
			case DUE -> "resolutionDueAt";
			case START, CREATED -> "createdAt";
		};
		return TicketSpecifications.assignee(person.getId())
			.and(TicketSpecifications.visibleTo(access))
			.and(TicketSpecifications.statusOrOverdue(statuses, overdue, now))
			.and(TicketSpecifications.category(filter.groupId()))
			.and(TicketSpecifications.priorityIn(priorities))
			.and(TicketSpecifications.matches(filter.search()))
			.and(TicketSpecifications.instantBetween(dateAttribute, startOf(filter.from()), startOfNext(filter.to())));
	}

	private TaskRow taskRow(Task task, LocalDate today) {
		LocalDate completed = task.getCompletedAt() == null ? null : date(task.getCompletedAt());
		boolean overdue = task.getStatus().isActive() && task.getDueDate() != null && task.getDueDate().isBefore(today);
		Boolean onTime = task.getStatus() == TaskStatus.COMPLETED ? WorkHistoryRules.onTime(completed, task.getDueDate())
				: null;
		Integer daysLate = task.getStatus() == TaskStatus.COMPLETED || overdue
				? WorkHistoryRules.daysLate(task.getDueDate(), task.getStatus() == TaskStatus.COMPLETED ? completed : null, today)
				: null;
		return new TaskRow(task.getId(), task.getCode(), task.getTitle(), ProjectRef.of(task.getProject()),
				task.getPriority(), task.getStatus(), task.getStartDate(), task.getDueDate(), completed,
				task.getEstimatedHours(), task.getActualHours(), overdue, onTime, daysLate);
	}

	private TicketRow ticketRow(Ticket ticket, Instant now) {
		SlaCalculator.Status sla = SlaCalculator.evaluate(ticket.resolutionClock(), now);
		boolean overdue = ticket.getStatus().clockRuns() && sla.dueAt() != null && now.isAfter(sla.dueAt());
		LocalDate resolved = ticket.getResolvedAt() == null || !ticket.getStatus().isDone() ? null : date(ticket.getResolvedAt());
		Integer daysLate = null;
		if (sla.dueAt() != null && (overdue || Boolean.FALSE.equals(sla.met()))) {
			Instant end = resolved != null ? ticket.getResolvedAt() : now;
			daysLate = (int) Math.max(1, (ChronoUnit.MINUTES.between(sla.dueAt(), end) + 1439) / 1440);
		}
		return new TicketRow(ticket.getId(), ticket.getCode(), ticket.getSubject(),
				ticket.getCategory() == null ? null : ticket.getCategory().getName(), ticket.getPriority(),
				ticket.getStatus(), date(ticket.getCreatedAt()), sla.dueAt(), resolved, sla.state(), overdue,
				resolved == null ? null : sla.met(), daysLate);
	}

	private static void requireRange(Filter filter) {
		if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
			throw ApiException.badRequest("INVALID_RANGE", "'to' must be on or after 'from'");
		}
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
		try {
			return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.badRequest("INVALID_FILTER", "Unknown " + field + ": " + value);
		}
	}

	private static Set<String> nonNull(Set<String> values) {
		return values == null ? Set.of() : values;
	}

	/** "Yes", "Late by 3 days", "Overdue by 2 days", or empty. */
	static String timeliness(Boolean onTime, Integer daysLate, boolean overdue) {
		if (overdue && daysLate != null) {
			return "Overdue by " + days(daysLate);
		}
		if (Boolean.TRUE.equals(onTime)) {
			return "Yes";
		}
		return daysLate != null ? "Late by " + days(daysLate) : "";
	}

	private static String days(int days) {
		return days + (days == 1 ? " day" : " days");
	}

	private String fileName(Long userId, String what) {
		String name = userRepository.findById(userId).map(User::getFullName).orElse("member");
		String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return (slug.isEmpty() ? "member" : slug) + "-" + what + ".csv";
	}

	private LocalDate date(Instant instant) {
		return instant == null ? null : LocalDate.ofInstant(instant, calendar.zone());
	}

	private Instant startOf(LocalDate date) {
		return date == null ? null : calendar.startOf(date);
	}

	private Instant startOfNext(LocalDate date) {
		return date == null ? null : calendar.startOf(date.plusDays(1));
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

}
