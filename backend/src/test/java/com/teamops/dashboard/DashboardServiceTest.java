package com.teamops.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.dashboard.DashboardDtos.DepartmentRow;
import com.teamops.dashboard.DashboardDtos.Response;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.sla.dto.SlaDtos;
import com.teamops.sla.service.SlaService;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.task.entity.Task;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.dto.UserSummary;
import com.teamops.workload.WorkloadDtos;
import com.teamops.workload.WorkloadLevel;
import com.teamops.workload.WorkloadService;

class DashboardServiceTest {

	/** 10:00 UTC on Thursday 8 October 2026 = 15:30 IST. */
	private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

	private static final DepartmentSummary WEBDEV = new DepartmentSummary(5L, "Web Development", "WEBDEV");

	private final DashboardQuery query = mock(DashboardQuery.class);

	private final TaskRepository taskRepository = mock(TaskRepository.class);

	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

	private final WorkloadService workloadService = mock(WorkloadService.class);

	private final AccessScopeService scopes = mock(AccessScopeService.class);

	private final SlaService slaService = mock(SlaService.class);

	private DashboardService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		service = new DashboardService(query, taskRepository, departmentRepository, workloadService, scopes,
				slaService, new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"));
		when(query.taskCounts(any(), any(), any(), any()))
			.thenReturn(new DashboardQuery.TaskCounts(9, 2, 3, 4, 4, 3, 1, 1, 7));
		when(query.weeklyCompletions(any(), any(), anyInt())).thenReturn(Map.of());
		when(query.recentActivity(any(), anyInt())).thenReturn(List.of());
		when(slaService.openTicketKpis(any())).thenReturn(new SlaDtos.TicketKpis(2, 1));
		when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn((Page<Task>) new PageImpl<Task>(List.of()));
	}

	@Test
	void departmentWorkloadIsRemainingOverCapacityAndOnTimeIgnoresUndatedTasks() {
		List<WorkloadDtos.Row> people = List.of(row(4L, "60.0", "80.00"), row(9L, "36.0", "80.00"));

		DepartmentRow department = DashboardService.departmentRow(WEBDEV,
				new DashboardQuery.DepartmentCounts(12, 3, 10, 8, 6), people);

		assertThat(department.people()).isEqualTo(2);
		assertThat(department.workloadPercent()).as("96 h of 160 h").isEqualTo(60);
		assertThat(department.level()).isEqualTo(WorkloadLevel.NORMAL);
		assertThat(department.onTimePercent()).as("6 of the 8 dated completions").isEqualTo(75);
		assertThat(department.completed()).isEqualTo(10);
	}

	@Test
	void departmentWithoutPeopleOrCompletionsHasNoRates() {
		DepartmentRow department = DashboardService.departmentRow(WEBDEV, DashboardQuery.DepartmentCounts.EMPTY,
				List.of());

		assertThat(department.workloadPercent()).isNull();
		assertThat(department.level()).isNull();
		assertThat(department.onTimePercent()).isNull();
	}

	@Test
	void employeeGetsAPersonalDashboardWithoutTeamSections() {
		when(scopes.scopeFor(SliceAuth.EMPLOYEE)).thenReturn(AccessScope.own(4L));
		when(workloadService.workload(any(), eq(SliceAuth.EMPLOYEE)))
			.thenReturn(workload(List.of(row(4L, "20.0", "80.00"))));

		Response response = service.dashboard(SliceAuth.EMPLOYEE);

		assertThat(response.scope()).isEqualTo(AccessScope.Kind.OWN);
		assertThat(response.today()).isEqualTo(LocalDate.of(2026, 10, 8));
		assertThat(response.weekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
		assertThat(response.kpis().teamMembers()).isNull();
		assertThat(response.kpis().openTickets()).isEqualTo(2);
		assertThat(response.kpis().slaBreaches()).isEqualTo(1);
		assertThat(response.kpis().pendingApprovals()).as("approvals not built yet").isNull();
		assertThat(response.kpis().overdue()).isEqualTo(3);
		assertThat(response.departments()).isEmpty();
		assertThat(response.weeklyCompletion()).hasSize(DashboardService.WEEKS);
		verify(workloadService).workload(argThat(c -> Long.valueOf(4L).equals(c.userId())), eq(SliceAuth.EMPLOYEE));
		verify(query, never()).departmentCounts(any(), any(), any(), anyInt());
	}

	@Test
	void superAdminSeesEveryActiveDepartmentAndTheWholeTeam() {
		Department webDev = TestFixtures.department(5L, "WEBDEV", "Web Development");
		when(scopes.scopeFor(SliceAuth.SUPER_ADMIN)).thenReturn(AccessScope.all(1L));
		when(departmentRepository.findAllByOrderByNameAsc()).thenReturn(List.of(webDev));
		when(query.departmentCounts(eq(List.of(5L)), any(), any(), eq(19800)))
			.thenReturn(Map.of(5L, new DashboardQuery.DepartmentCounts(5, 1, 2, 2, 2)));
		when(workloadService.workload(any(), eq(SliceAuth.SUPER_ADMIN)))
			.thenReturn(workload(List.of(row(4L, "100.0", "80.00"), row(9L, "4.0", "80.00"))));

		Response response = service.dashboard(SliceAuth.SUPER_ADMIN);

		assertThat(response.kpis().teamMembers()).isEqualTo(2);
		assertThat(response.departments()).singleElement().satisfies(d -> {
			assertThat(d.openTasks()).isEqualTo(5);
			assertThat(d.onTimePercent()).isEqualTo(100);
			assertThat(d.workloadPercent()).isEqualTo(65);
		});
		assertThat(response.statusDistribution()).hasSize(5);
		verify(workloadService).workload(argThat(c -> c.userId() == null), eq(SliceAuth.SUPER_ADMIN));
	}

	private static WorkloadDtos.Row row(Long userId, String remaining, String capacity) {
		int percent = DashboardMath.percent(new BigDecimal(remaining), new BigDecimal(capacity));
		return new WorkloadDtos.Row(new UserSummary(userId, "Person " + userId, userId + "@teamops.local", null, null),
				WEBDEV, 0, 0, 0, 0, 0, 0, 0, 0, 0, new BigDecimal(remaining), new BigDecimal(capacity), percent,
				WorkloadLevel.of(percent));
	}

	private static WorkloadDtos.Response workload(List<WorkloadDtos.Row> rows) {
		Map<WorkloadLevel, Long> byLevel = new EnumMap<>(WorkloadLevel.class);
		return new WorkloadDtos.Response(LocalDate.of(2026, 10, 8), 14, new BigDecimal("4"), null, null,
				new WorkloadDtos.Summary(rows.size(), byLevel, 0, 0, 0, 0), rows);
	}

}
