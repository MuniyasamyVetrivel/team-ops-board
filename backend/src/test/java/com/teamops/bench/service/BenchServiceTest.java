package com.teamops.bench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.bench.dto.BenchDtos.BenchDashboard;
import com.teamops.bench.dto.BenchDtos.BenchEntry;
import com.teamops.bench.dto.BenchDtos.SkillCount;
import com.teamops.bench.dto.BenchDtos.StatusCount;
import com.teamops.bench.entity.Allocation;
import com.teamops.bench.entity.Client;
import com.teamops.bench.entity.ClientEngagement;
import com.teamops.bench.entity.Consultant;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.repository.AllocationRepository;
import com.teamops.bench.repository.AllocationRepository.Slice;
import com.teamops.bench.repository.ConsultantRepository;
import com.teamops.bench.service.BenchService.BenchFilter;
import com.teamops.celebration.entity.Employee;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.web.PageResponse;

/**
 * Bench and dashboard figures on 16 October 2026, for six consultants: A fully allocated, B over-allocated (60%
 * billable + 60% non-billable), C at 50% after rolling off a full allocation on 30 September, D unallocated since being
 * added on 6 October, E unallocated but only available from 1 November, F on notice.
 */
class BenchServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

	private final ConsultantRepository consultants = mock(ConsultantRepository.class);

	private final AllocationRepository allocations = mock(AllocationRepository.class);

	private final BenchService service = new BenchService(consultants, allocations,
			new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-16T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"));

	private final List<Slice> slices = new ArrayList<>();

	@BeforeEach
	void setUp() {
		Consultant a = consultant(1L, "Asha", EmploymentStatus.ACTIVE, null, "2026-01-01");
		Consultant b = consultant(2L, "Bala", EmploymentStatus.ACTIVE, null, "2026-01-01");
		Consultant c = consultant(3L, "Chitra", EmploymentStatus.ACTIVE, null, "2026-01-01", "Tosca", "SAP S/4HANA");
		Consultant d = consultant(4L, "Dev", EmploymentStatus.ACTIVE, null, "2026-10-06", "tosca");
		Consultant e = consultant(5L, "Esha", EmploymentStatus.ACTIVE, LocalDate.of(2026, 11, 1), "2026-01-01", "Java");
		Consultant f = consultant(6L, "Farid", EmploymentStatus.NOTICE_PERIOD, null, "2026-01-01", "Java");
		when(consultants.findAllByOrderByIdAsc()).thenReturn(List.of(a, b, c, d, e, f));
		slice(1L, "2026-01-01", null, 100, true);
		slice(2L, "2026-01-01", "2026-12-31", 60, true);
		slice(2L, "2026-10-01", "2026-10-31", 60, false);
		slice(3L, "2026-06-01", "2026-09-30", 100, true);
		slice(3L, "2026-10-01", "2026-11-05", 50, false);
		when(allocations.findSlices(anyCollection())).thenReturn(slices);
		when(allocations.findByEndDateBetweenOrderByEndDateAscIdAsc(any(), any()))
			.thenReturn(List.of(allocation(c, "2026-11-05")));
	}

	@Test
	void theDashboardCapsEachConsultantAt100PercentAndCountsBillableFirst() {
		BenchDashboard dashboard = service.dashboard();

		assertThat(dashboard.asOf()).isEqualTo(TODAY);
		assertThat(dashboard.byStatus()).containsExactly(new StatusCount(ConsultantStatus.ALLOCATED, 2),
				new StatusCount(ConsultantStatus.PARTIALLY_ALLOCATED, 1), new StatusCount(ConsultantStatus.ON_BENCH, 2),
				new StatusCount(ConsultantStatus.LEAVE_OF_ABSENCE, 0), new StatusCount(ConsultantStatus.NOTICE_PERIOD, 1),
				new StatusCount(ConsultantStatus.LEFT, 0));
		// Capacity: A, B, C, D (E is not available yet, F is on notice).
		assertThat(dashboard.capacityConsultants()).isEqualTo(4);
		assertThat(dashboard.capacityFte()).isEqualByComparingTo("4.00");
		assertThat(dashboard.allocatedFte()).isEqualByComparingTo("2.50");
		assertThat(dashboard.billableFte()).isEqualByComparingTo("1.60");
		assertThat(dashboard.nonBillableFte()).isEqualByComparingTo("0.90");
		assertThat(dashboard.benchFte()).isEqualByComparingTo("1.50");
		assertThat(dashboard.utilisationPercent()).isEqualByComparingTo("62.5");
		assertThat(dashboard.billablePercent()).isEqualByComparingTo("40.0");
		assertThat(dashboard.nonBillablePercent()).isEqualByComparingTo("22.5");
	}

	@Test
	void theDashboardShowsTheBenchItsDaysRollOffsAndSkills() {
		BenchDashboard dashboard = service.dashboard();

		assertThat(dashboard.onBenchNow()).isEqualTo(2);
		assertThat(dashboard.unallocated()).isEqualTo(1);
		assertThat(dashboard.longestOnBench()).extracting(BenchEntry::consultantId).containsExactly(3L, 4L);
		assertThat(dashboard.longestOnBench()).extracting(BenchEntry::daysOnBench).containsExactly(15L, 10L);
		assertThat(dashboard.longestOnBench().get(0).benchSince()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(dashboard.longestOnBench().get(0).benchPercent()).isEqualTo(50);
		assertThat(dashboard.averageDaysOnBench()).isEqualByComparingTo("12.5");
		assertThat(dashboard.longestDaysOnBench()).isEqualTo(15);
		assertThat(dashboard.upcomingRollOffCount()).isEqualTo(1);
		assertThat(dashboard.upcomingRollOffs().get(0).daysUntilEnd()).isEqualTo(20);
		assertThat(dashboard.benchSkills()).containsExactly(new SkillCount("Tosca", 2), new SkillCount("SAP S/4HANA", 1));
	}

	@Test
	void withNoConsultantsThePercentagesAreEmptyNotZero() {
		when(consultants.findAllByOrderByIdAsc()).thenReturn(List.of());
		when(allocations.findByEndDateBetweenOrderByEndDateAscIdAsc(any(), any())).thenReturn(List.of());

		BenchDashboard dashboard = service.dashboard();

		assertThat(dashboard.utilisationPercent()).isNull();
		assertThat(dashboard.billablePercent()).isNull();
		assertThat(dashboard.averageDaysOnBench()).isNull();
		assertThat(dashboard.capacityFte()).isEqualByComparingTo("0");
	}

	@Test
	void theBenchListFiltersSortsAndPagesInMemory() {
		PageResponse<BenchEntry> first = service.bench(new BenchFilter(null, null),
				PageRequest.of(0, 1, Sort.by(Sort.Order.desc("daysOnBench"))));
		assertThat(first.totalElements()).isEqualTo(2);
		assertThat(first.totalPages()).isEqualTo(2);
		assertThat(first.content()).extracting(BenchEntry::consultantId).containsExactly(3L);

		assertThat(service.bench(new BenchFilter(null, "TOSCA"), PageRequest.of(0, 10, Sort.by("name"))).content())
			.extracting(BenchEntry::consultantId)
			.containsExactly(3L, 4L);
		assertThat(service.bench(new BenchFilter("s/4", null), PageRequest.of(0, 10, Sort.by("name"))).content())
			.extracting(BenchEntry::consultantId)
			.containsExactly(3L);
		assertThat(service.bench(new BenchFilter("dev", null), PageRequest.of(0, 10, Sort.by("allocatedPercent")))
			.content()).extracting(BenchEntry::consultantId).containsExactly(4L);
	}

	private Consultant consultant(long id, String firstName, EmploymentStatus status, LocalDate availableFrom,
			String addedOn, String... skills) {
		Employee employee = new Employee();
		ReflectionTestUtils.setField(employee, "id", id + 100);
		employee.setEmployeeCode("TRI-" + id);
		employee.setFirstName(firstName);
		employee.setLastName("Test");
		Consultant consultant = new Consultant();
		ReflectionTestUtils.setField(consultant, "id", id);
		ReflectionTestUtils.setField(consultant, "createdAt", Instant.parse(addedOn + "T06:00:00Z"));
		consultant.setEmployeeId(id + 100);
		consultant.setEmployee(employee);
		consultant.setEmploymentStatus(status);
		consultant.setAvailableFrom(availableFrom);
		ConsultantService.replaceSkills(consultant, List.of(skills));
		return consultant;
	}

	private void slice(long consultantId, String start, String end, int percent, boolean billable) {
		long id = slices.size() + 1;
		slices.add(new Slice() {

			@Override
			public Long getId() {
				return id;
			}

			@Override
			public Long getConsultantId() {
				return consultantId;
			}

			@Override
			public LocalDate getStartDate() {
				return LocalDate.parse(start);
			}

			@Override
			public LocalDate getEndDate() {
				return end == null ? null : LocalDate.parse(end);
			}

			@Override
			public int getAllocationPercent() {
				return percent;
			}

			@Override
			public boolean isBillable() {
				return billable;
			}

		});
	}

	static Allocation allocation(Consultant consultant, String end) {
		Client client = new Client();
		client.setName("Acme");
		ClientEngagement engagement = new ClientEngagement();
		engagement.setClient(client);
		engagement.setName("S/4 rollout");
		Allocation allocation = new Allocation();
		ReflectionTestUtils.setField(allocation, "id", 90L);
		allocation.setConsultant(consultant);
		allocation.setEngagement(engagement);
		allocation.setRole("Test Lead");
		allocation.setStartDate(LocalDate.of(2026, 10, 1));
		allocation.setEndDate(LocalDate.parse(end));
		allocation.setAllocationPercent(50);
		allocation.setBillable(false);
		return allocation;
	}

}
