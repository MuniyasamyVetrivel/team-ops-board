package com.teamops.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.dashboard.DashboardMath;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.project.dto.ProjectDtos.ProjectListItem;
import com.teamops.project.entity.ProjectStatus;
import com.teamops.project.service.ProjectService;
import com.teamops.report.dto.ReportDtos.AgeBucket;
import com.teamops.report.dto.ReportDtos.DepartmentWorkload;
import com.teamops.report.dto.ReportDtos.EmployeeRow;
import com.teamops.report.dto.ReportDtos.ProjectReport;
import com.teamops.report.dto.ReportDtos.ProjectRow;
import com.teamops.report.dto.ReportDtos.ProjectStatusCount;
import com.teamops.report.dto.ReportDtos.Range;
import com.teamops.report.dto.ReportDtos.StatusCount;
import com.teamops.report.dto.ReportDtos.TaskDepartmentRow;
import com.teamops.report.dto.ReportDtos.TaskReport;
import com.teamops.report.dto.ReportDtos.TaskSummary;
import com.teamops.report.dto.ReportDtos.TicketDepartmentRow;
import com.teamops.report.dto.ReportDtos.TicketPriorityRow;
import com.teamops.report.dto.ReportDtos.TicketReport;
import com.teamops.report.dto.ReportDtos.TicketStatusCount;
import com.teamops.report.dto.ReportDtos.TicketSummary;
import com.teamops.report.dto.ReportDtos.WeekRow;
import com.teamops.report.dto.ReportDtos.WorkloadReport;
import com.teamops.report.repository.TaskReportQuery;
import com.teamops.report.repository.TaskReportQuery.Sums;
import com.teamops.sla.service.SlaCalculator;
import com.teamops.sla.service.SlaState;
import com.teamops.task.entity.TaskStatus;
import com.teamops.ticket.dto.TicketDtos.TicketSla;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.ticket.repository.TicketSpecifications;
import com.teamops.ticket.service.TicketService;
import com.teamops.workload.WorkloadDtos;
import com.teamops.workload.WorkloadLevel;
import com.teamops.workload.WorkloadService;

import lombok.RequiredArgsConstructor;

/**
 * Management reports (brief sections 20 and 78): task completion and productivity, workload, ticket performance and
 * project progress, each within the viewer's scope (the same rules as the pages they summarise) and for a range of
 * business days (default the last 30, at most 366).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

	public static final int DEFAULT_RANGE_DAYS = 30;

	public static final int MAX_RANGE_DAYS = 366;

	static final int MAX_PROJECTS = 500;

	private static final List<TicketPriority> PRIORITIES = List.of(TicketPriority.URGENT, TicketPriority.HIGH,
			TicketPriority.MEDIUM, TicketPriority.LOW);

	private final TaskReportQuery taskQuery;

	private final AccessScopeService accessScopeService;

	private final WorkloadService workloadService;

	private final TicketRepository ticketRepository;

	private final TicketService ticketService;

	private final ProjectService projectService;

	private final BusinessCalendar calendar;

	/** {@code to} defaults to today and {@code from} to 29 days before it; 400 {@code INVALID_RANGE} otherwise. */
	public Range range(LocalDate from, LocalDate to) {
		LocalDate end = to == null ? calendar.today() : to;
		LocalDate start = from == null ? end.minusDays(DEFAULT_RANGE_DAYS - 1L) : from;
		if (start.isAfter(end)) {
			throw ApiException.badRequest("INVALID_RANGE", "The start date cannot be after the end date");
		}
		if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
			throw ApiException.badRequest("INVALID_RANGE", "A report covers at most " + MAX_RANGE_DAYS + " days");
		}
		return new Range(start, end);
	}

	// --- tasks ------------------------------------------------------------------------------------------------

	/** Task completion, overdue %, department performance, employee productivity and the weekly trend. */
	public TaskReport tasks(Range range, Long departmentId, Long assigneeId, Long projectId, Set<TaskStatus> statuses,
			AuthenticatedUser actor) {
		TaskReportQuery.Filter filter = new TaskReportQuery.Filter(accessScopeService.scopeFor(actor),
				calendar.startOf(range.from()), calendar.startOf(range.to().plusDays(1)), calendar.today(),
				offsetSeconds(), departmentId, assigneeId, projectId, statuses);
		Sums total = taskQuery.summary(filter);
		Map<TaskStatus, Long> open = taskQuery.openByStatus(filter);
		List<StatusCount> openByStatus = new ArrayList<>();
		for (TaskStatus status : TaskStatus.ACTIVE) {
			openByStatus.add(new StatusCount(status, open.getOrDefault(status, 0L)));
		}
		List<TaskDepartmentRow> departments = taskQuery.byDepartment(filter)
			.stream()
			.map(d -> new TaskDepartmentRow(d.department(), d.sums().created(), d.sums().completed(), onTime(d.sums()),
					d.sums().open(), d.sums().overdue(), DashboardMath.percent(d.sums().overdue(), d.sums().open())))
			.toList();
		List<EmployeeRow> employees = taskQuery.byAssignee(filter)
			.stream()
			.map(a -> new EmployeeRow(a.user(), a.department(), a.sums().completed(), onTime(a.sums()), a.sums().open(),
					a.sums().overdue(), a.sums().hoursLogged()))
			.toList();
		Map<LocalDate, TaskReportQuery.Week> weeks = taskQuery.weekly(filter);
		List<WeekRow> trend = new ArrayList<>();
		for (LocalDate week = weekStart(range.from()); !week.isAfter(range.to()); week = week.plusWeeks(1)) {
			TaskReportQuery.Week w = weeks.get(week);
			trend.add(new WeekRow(week, w == null ? 0 : w.created(), w == null ? 0 : w.completed()));
		}
		TaskSummary summary = new TaskSummary(total.created(), total.completed(), total.completedWithDue(),
				total.completedOnTime(), onTime(total), total.open(), total.overdue(),
				DashboardMath.percent(total.overdue(), total.open()), total.hoursLogged());
		return new TaskReport(range, summary, openByStatus, departments, employees, trend);
	}

	// --- workload ---------------------------------------------------------------------------------------------

	/** Employee workload (as on the Workload page) and the same rolled up per department. */
	public WorkloadReport workload(Long departmentId, Long userId, AuthenticatedUser actor) {
		WorkloadDtos.Response employees = workloadService.workload(new WorkloadDtos.Criteria(departmentId, userId, null,
				null, null, null, null, WorkloadDtos.Sort.HIGHEST), actor);
		Map<Long, List<WorkloadDtos.Row>> byDepartment = new LinkedHashMap<>();
		Map<Long, DepartmentSummary> departments = new LinkedHashMap<>();
		for (WorkloadDtos.Row row : employees.rows()) {
			byDepartment.computeIfAbsent(row.department().id(), id -> new ArrayList<>()).add(row);
			departments.putIfAbsent(row.department().id(), row.department());
		}
		List<DepartmentWorkload> rows = new ArrayList<>();
		byDepartment.forEach((id, people) -> {
			BigDecimal remaining = BigDecimal.ZERO;
			BigDecimal capacity = BigDecimal.ZERO;
			long active = 0;
			long overdue = 0;
			for (WorkloadDtos.Row person : people) {
				remaining = remaining.add(person.remainingHours());
				capacity = capacity.add(person.capacityHours());
				active += person.activeTasks();
				overdue += person.overdue();
			}
			Integer percent = DashboardMath.percent(remaining, capacity);
			rows.add(new DepartmentWorkload(departments.get(id), people.size(), active, overdue, remaining, capacity,
					percent, percent == null ? null : WorkloadLevel.of(percent)));
		});
		rows.sort((a, b) -> Integer.compare(b.workloadPercent() == null ? -1 : b.workloadPercent(),
				a.workloadPercent() == null ? -1 : a.workloadPercent()));
		return new WorkloadReport(employees, rows);
	}

	// --- tickets ----------------------------------------------------------------------------------------------

	/**
	 * Tickets the viewer can see: created and resolved in the range, SLA compliance of those created in it, and the
	 * open ones now by age, status, priority and department. Average resolution is wall-clock hours from creation.
	 */
	public TicketReport tickets(Range range, Long departmentId, Long assigneeId, AuthenticatedUser actor) {
		Instant from = calendar.startOf(range.from());
		Instant to = calendar.startOf(range.to().plusDays(1));
		Instant now = calendar.now();
		Specification<Ticket> inReport = (root, query, cb) -> cb.or(root.get("status").in(TicketStatus.OPEN_STATUSES),
				cb.and(cb.greaterThanOrEqualTo(root.get("createdAt"), from), cb.lessThan(root.get("createdAt"), to)),
				cb.and(cb.greaterThanOrEqualTo(root.get("resolvedAt"), from), cb.lessThan(root.get("resolvedAt"), to)));
		Specification<Ticket> spec = TicketSpecifications.visibleTo(ticketService.access(actor)).and(inReport);
		if (departmentId != null) {
			spec = spec.and(TicketSpecifications.department(departmentId));
		}
		if (assigneeId != null) {
			spec = spec.and(TicketSpecifications.assignee(assigneeId));
		}
		List<Ticket> tickets = ticketRepository.findAll(spec, Pageable.unpaged()).getContent();

		TicketTally total = new TicketTally();
		Map<TicketPriority, TicketTally> byPriority = new EnumMap<>(TicketPriority.class);
		Map<Long, TicketTally> byDepartment = new LinkedHashMap<>();
		Map<Long, DepartmentSummary> departments = new LinkedHashMap<>();
		Map<TicketStatus, Long> openByStatus = new EnumMap<>(TicketStatus.class);
		long[] ages = new long[AGE_LABELS.length];
		long resolvedMinutes = 0;
		for (Ticket ticket : tickets) {
			TicketSla sla = TicketSla.of(ticket, now);
			boolean created = !ticket.getCreatedAt().isBefore(from) && ticket.getCreatedAt().isBefore(to);
			boolean resolved = ticket.getResolvedAt() != null && !ticket.getResolvedAt().isBefore(from)
					&& ticket.getResolvedAt().isBefore(to);
			boolean open = ticket.getStatus().isOpen();
			DepartmentSummary department = DepartmentSummary.of(ticket.getDepartment());
			departments.putIfAbsent(department.id(), department);
			for (TicketTally tally : List.of(total, byPriority.computeIfAbsent(ticket.getPriority(), p -> new TicketTally()),
					byDepartment.computeIfAbsent(department.id(), id -> new TicketTally()))) {
				tally.add(sla, created, resolved, open);
			}
			if (resolved) {
				resolvedMinutes += Duration.between(ticket.getCreatedAt(), ticket.getResolvedAt()).toMinutes();
			}
			if (open) {
				openByStatus.merge(ticket.getStatus(), 1L, Long::sum);
				ages[ageBucket(Duration.between(ticket.getCreatedAt(), now).toHours())]++;
			}
		}

		List<TicketPriorityRow> priorities = new ArrayList<>();
		for (TicketPriority priority : PRIORITIES) {
			TicketTally t = byPriority.getOrDefault(priority, new TicketTally());
			priorities.add(new TicketPriorityRow(priority, t.created, t.resolved, t.open,
					SlaCalculator.compliancePercent(t.firstResponse), SlaCalculator.compliancePercent(t.resolution)));
		}
		List<TicketDepartmentRow> departmentRows = new ArrayList<>();
		byDepartment.forEach((id, t) -> departmentRows.add(new TicketDepartmentRow(departments.get(id), t.created,
				t.resolved, t.open, t.openBreached, SlaCalculator.compliancePercent(t.resolution))));
		departmentRows.sort((a, b) -> a.department().name().compareToIgnoreCase(b.department().name()));
		List<AgeBucket> ageing = new ArrayList<>();
		for (int i = 0; i < AGE_LABELS.length; i++) {
			ageing.add(new AgeBucket(AGE_LABELS[i], ages[i]));
		}
		List<TicketStatusCount> statuses = new ArrayList<>();
		for (TicketStatus status : TicketStatus.OPEN_STATUSES) {
			statuses.add(new TicketStatusCount(status, openByStatus.getOrDefault(status, 0L)));
		}
		BigDecimal averageHours = total.resolved == 0 ? null
				: BigDecimal.valueOf(resolvedMinutes).divide(BigDecimal.valueOf(total.resolved * 60), 2, RoundingMode.HALF_UP);
		TicketSummary summary = new TicketSummary(total.created, total.resolved, total.open, total.openBreached,
				SlaCalculator.compliancePercent(total.firstResponse), SlaCalculator.compliancePercent(total.resolution),
				averageHours);
		return new TicketReport(range, summary, priorities, departmentRows, ageing, statuses);
	}

	static final String[] AGE_LABELS = { "Under 1 day", "1–3 days", "3–7 days", "7–30 days", "Over 30 days" };

	/** The age bucket of an open ticket, by whole hours since it was created. */
	static int ageBucket(long hours) {
		if (hours < 24) {
			return 0;
		}
		if (hours < 72) {
			return 1;
		}
		if (hours < 168) {
			return 2;
		}
		return hours < 720 ? 3 : 4;
	}

	/** Counts and SLA statuses of a group of tickets; compliance counts only tickets created in the range. */
	private static final class TicketTally {

		long created;

		long resolved;

		long open;

		long openBreached;

		final List<SlaCalculator.Status> firstResponse = new ArrayList<>();

		final List<SlaCalculator.Status> resolution = new ArrayList<>();

		void add(TicketSla sla, boolean created, boolean resolved, boolean open) {
			if (created) {
				this.created++;
				firstResponse.add(sla.firstResponse());
				resolution.add(sla.resolution());
			}
			if (resolved) {
				this.resolved++;
			}
			if (open) {
				this.open++;
				if (sla.overall() == SlaState.BREACHED) {
					openBreached++;
				}
			}
		}

	}

	// --- projects ---------------------------------------------------------------------------------------------

	/** Project progress for the projects the viewer can see (at most {@value #MAX_PROJECTS}, by name). */
	public ProjectReport projects(Long departmentId, Set<ProjectStatus> statuses, AuthenticatedUser actor) {
		LocalDate today = calendar.today();
		List<ProjectListItem> items = projectService
			.search(null, statuses, departmentId, PageRequest.of(0, MAX_PROJECTS, Sort.by("name", "id")), actor)
			.content();
		Map<ProjectStatus, Long> counts = new EnumMap<>(ProjectStatus.class);
		List<ProjectRow> rows = new ArrayList<>();
		for (ProjectListItem p : items) {
			counts.merge(p.status(), 1L, Long::sum);
			boolean finished = p.status() == ProjectStatus.COMPLETED || p.status() == ProjectStatus.CANCELLED;
			rows.add(new ProjectRow(p.id(), p.code(), p.name(), p.status(), p.department(), p.owner(), p.startDate(),
					p.endDate(), p.progress(), p.tasks().total(), p.tasks().completed(), p.tasks().overdue(),
					p.milestones().total(), p.milestones().completed(), p.milestones().overdue(), p.openRisks(),
					!finished && p.endDate() != null && p.endDate().isBefore(today)));
		}
		List<ProjectStatusCount> byStatus = new ArrayList<>();
		for (ProjectStatus status : ProjectStatus.values()) {
			byStatus.add(new ProjectStatusCount(status, counts.getOrDefault(status, 0L)));
		}
		return new ProjectReport(today, byStatus, rows);
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private static Integer onTime(Sums sums) {
		return DashboardMath.percent(sums.completedOnTime(), sums.completedWithDue());
	}

	private static LocalDate weekStart(LocalDate date) {
		return date.minusDays(date.getDayOfWeek().getValue() - 1L);
	}

	/** Today's offset of the business zone (exact for zones without daylight saving, such as Asia/Kolkata). */
	private int offsetSeconds() {
		return calendar.zone().getRules().getOffset(calendar.now()).getTotalSeconds();
	}

}
