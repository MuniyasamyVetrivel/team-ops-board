package com.teamops.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.dashboard.DashboardDtos.DepartmentRow;
import com.teamops.dashboard.DashboardDtos.Kpis;
import com.teamops.dashboard.DashboardDtos.Response;
import com.teamops.dashboard.DashboardDtos.StatusSlice;
import com.teamops.dashboard.DashboardDtos.WorkloadPanel;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.sla.dto.SlaDtos;
import com.teamops.sla.service.SlaService;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.task.dto.DueFilter;
import com.teamops.task.dto.TaskListItem;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.repository.TaskRepository;
import com.teamops.task.repository.TaskSpecifications;
import com.teamops.workload.WorkloadDtos;
import com.teamops.workload.WorkloadLevel;
import com.teamops.workload.WorkloadService;

import lombok.RequiredArgsConstructor;

/**
 * Home dashboard (brief sections 8, 67, 68 and 82). One endpoint serves every role; the data follows the viewer's
 * scope: the whole company for a Super Admin, managed departments for a manager, and the viewer's own work for an
 * employee (who gets no department or team sections).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

	static final int COMPLETED_WINDOW_DAYS = 30;

	static final int WEEKS = 8;

	static final int LIST_SIZE = 8;

	static final int UPCOMING_DAYS = 7;

	static final int ACTIVITY_SIZE = 12;

	static final int WORKLOAD_ROWS = 10;

	private final DashboardQuery query;

	private final TaskRepository taskRepository;

	private final DepartmentRepository departmentRepository;

	private final WorkloadService workloadService;

	private final AccessScopeService accessScopeService;

	private final SlaService slaService;

	private final BusinessCalendar calendar;

	public Response dashboard(AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		boolean personal = scope.kind() == AccessScope.Kind.OWN || !actor.hasPermission("WORKLOAD_VIEW");
		LocalDate today = calendar.today();
		LocalDate weekStart = calendar.startOfWeek();
		int offset = calendar.zone().getRules().getOffset(calendar.now()).getTotalSeconds();

		DashboardQuery.TaskCounts counts = query.taskCounts(scope, today, calendar.startOf(weekStart),
				calendar.startOf(today.minusDays(COMPLETED_WINDOW_DAYS)));

		WorkloadDtos.Response workload = workloadService.workload(new WorkloadDtos.Criteria(null,
				personal ? actor.id() : null, null, null, null, null, null, WorkloadDtos.Sort.HIGHEST), actor);

		SlaDtos.TicketKpis tickets = actor.hasPermission("TICKET_VIEW") ? slaService.openTicketKpis(actor) : null;
		Kpis kpis = new Kpis(counts.open(), counts.dueToday(), counts.overdue(), counts.completedThisWeek(),
				counts.inProgress(), counts.blocked(),
				scope.kind() == AccessScope.Kind.OWN ? null : (long) workload.summary().people(),
				tickets == null ? null : tickets.open(), tickets == null ? null : tickets.breached(), null);

		List<StatusSlice> distribution = List.of(new StatusSlice(TaskStatus.TODO, counts.todo()),
				new StatusSlice(TaskStatus.IN_PROGRESS, counts.inProgress()),
				new StatusSlice(TaskStatus.BLOCKED, counts.blocked()),
				new StatusSlice(TaskStatus.IN_REVIEW, counts.inReview()),
				new StatusSlice(TaskStatus.COMPLETED, counts.completedRecently()));

		List<DepartmentRow> departments = scope.kind() == AccessScope.Kind.OWN ? List.of()
				: departmentRows(scope, workload.rows(), today, offset);

		WorkloadPanel panel = new WorkloadPanel(workload.summary().people(), workload.summary().averagePercent(),
				workload.summary().byLevel(), workload.windowDays(),
				workload.rows().stream().limit(WORKLOAD_ROWS).toList());

		var work = TaskSpecifications.workOf(scope);
		List<TaskListItem> overdue = taskRepository
			.findAll(work.and(TaskSpecifications.due(DueFilter.OVERDUE, today)),
					PageRequest.of(0, LIST_SIZE, Sort.by("dueDate", "id")))
			.map(task -> TaskListItem.of(task, today))
			.getContent();
		List<TaskListItem> upcoming = taskRepository
			.findAll(work.and(TaskSpecifications.statusIn(TaskStatus.ACTIVE))
				.and(TaskSpecifications.dueBetween(today, today.plusDays(UPCOMING_DAYS))),
					PageRequest.of(0, LIST_SIZE, Sort.by("dueDate", "id")))
			.map(task -> TaskListItem.of(task, today))
			.getContent();

		return new Response(today, weekStart, scope.kind(), kpis, distribution, COMPLETED_WINDOW_DAYS, departments,
				panel,
				DashboardMath.weeklySeries(weekStart, WEEKS,
						query.weeklyCompletions(scope, calendar.startOf(weekStart.minusWeeks(WEEKS - 1)), offset)),
				overdue, upcoming, query.recentActivity(scope, ACTIVITY_SIZE));
	}

	private List<DepartmentRow> departmentRows(AccessScope scope, List<WorkloadDtos.Row> people, LocalDate today,
			int offset) {
		List<Department> departments = (scope.isAll() ? departmentRepository.findAllByOrderByNameAsc()
				: departmentRepository.findAllById(scope.departmentIds()))
			.stream()
			.filter(d -> d.getStatus() == DepartmentStatus.ACTIVE)
			.sorted(Comparator.comparing(Department::getName))
			.toList();
		Map<Long, DashboardQuery.DepartmentCounts> counts = query.departmentCounts(
				departments.stream().map(Department::getId).toList(), today,
				calendar.startOf(today.minusDays(COMPLETED_WINDOW_DAYS)), offset);
		Map<Long, List<WorkloadDtos.Row>> byDepartment = people.stream()
			.collect(Collectors.groupingBy(row -> row.department().id(), HashMap::new, Collectors.toList()));

		List<DepartmentRow> rows = new ArrayList<>(departments.size());
		for (Department department : departments) {
			DashboardQuery.DepartmentCounts c = counts.getOrDefault(department.getId(),
					DashboardQuery.DepartmentCounts.EMPTY);
			rows.add(departmentRow(DepartmentSummary.of(department), c,
					byDepartment.getOrDefault(department.getId(), List.of())));
		}
		return rows;
	}

	/** Department workload % = the department's remaining hours ÷ its people's capacity in the window. */
	static DepartmentRow departmentRow(DepartmentSummary department, DashboardQuery.DepartmentCounts counts,
			List<WorkloadDtos.Row> people) {
		BigDecimal remaining = BigDecimal.ZERO;
		BigDecimal capacity = BigDecimal.ZERO;
		for (WorkloadDtos.Row person : people) {
			remaining = remaining.add(person.remainingHours());
			capacity = capacity.add(person.capacityHours());
		}
		Integer percent = DashboardMath.percent(remaining, capacity);
		return new DepartmentRow(department, people.size(), counts.open(), counts.overdue(), counts.completed(),
				DashboardMath.percent(counts.completedOnTime(), counts.completedWithDueDate()), percent,
				percent == null ? null : WorkloadLevel.of(percent));
	}

}
