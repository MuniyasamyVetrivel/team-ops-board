package com.teamops.bench.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.bench.entity.Client;
import com.teamops.bench.entity.ClientEngagement;
import com.teamops.bench.entity.ClientStatus;
import com.teamops.bench.entity.Consultant;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.service.ConsultantStatus;
import com.teamops.bench.service.PeriodStatus;
import com.teamops.celebration.entity.Employee;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API records for Bench Management: consultants, clients and client engagements. */
public final class BenchDtos {

	private BenchDtos() {
	}

	// --- consultants ------------------------------------------------------------------------------------------

	/**
	 * The Employee record a consultant is (name, employee ID, designation, department, work email); {@code user} is the
	 * linked app user, for the avatar.
	 */
	public record ConsultantEmployee(Long id, String employeeCode, String fullName, String designation,
			DepartmentSummary department, String workEmail, UserSummary user) {

		public static ConsultantEmployee of(Employee employee) {
			if (employee == null) {
				return null;
			}
			return new ConsultantEmployee(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
					employee.getDesignation(),
					employee.getDepartment() == null ? null : DepartmentSummary.of(employee.getDepartment()),
					employee.getWorkEmail(), UserSummary.of(employee.getUser()));
		}

	}

	/** @param expired the expiry date has passed (business calendar); never stored */
	public record CertificationDto(Long id, String name, String issuer, LocalDate issuedOn, LocalDate expiresOn,
			String credentialId, boolean expired) {

	}

	/**
	 * @param employee null while the Employee record is in the recycle bin ({@code employeeId} still says which)
	 * @param status computed from the employment status and allocations; never stored
	 * @param allocatedPercent total allocation today (may exceed 100)
	 */
	public record ConsultantDto(Long id, Long employeeId, ConsultantEmployee employee, BigDecimal experienceYears,
			String location, LocalDate availableFrom, EmploymentStatus employmentStatus, ConsultantStatus status,
			int allocatedPercent, List<String> skills, List<CertificationDto> certifications, String notes, Integer version,
			Instant createdAt, Instant updatedAt) {

	}

	/** @param id an existing certification of this consultant to change; null for a new one */
	public record SaveCertification(Long id, @NotBlank @Size(max = 200) String name, @Size(max = 150) String issuer,
			LocalDate issuedOn, LocalDate expiresOn, @Size(max = 100) String credentialId) {

	}

	/**
	 * Skills and certifications replace the consultant's lists (order kept). {@code version} is required for an
	 * update (409 STALE_UPDATE when someone else saved first).
	 */
	public record SaveConsultant(@NotNull Long employeeId,
			@DecimalMin("0.0") @DecimalMax("60.0") @Digits(integer = 2, fraction = 1) BigDecimal experienceYears,
			@Size(max = 150) String location, LocalDate availableFrom, EmploymentStatus employmentStatus,
			@Size(max = 50) List<@Size(max = 100) String> skills,
			@Size(max = 30) List<@NotNull @Valid SaveCertification> certifications, @Size(max = 5000) String notes,
			Integer version) {

	}

	// --- clients ----------------------------------------------------------------------------------------------

	public record ClientRef(Long id, String name) {

		public static ClientRef of(Client client) {
			return client == null ? null : new ClientRef(client.getId(), client.getName());
		}

	}

	/** @param engagementCount live engagements with this client */
	public record ClientDto(Long id, String name, String location, ClientStatus status, String notes,
			long engagementCount, Integer version, Instant createdAt, Instant updatedAt) {

	}

	public record SaveClient(@NotBlank @Size(max = 200) String name, @Size(max = 150) String location,
			ClientStatus status, @Size(max = 5000) String notes, Integer version) {

	}

	// --- engagements ------------------------------------------------------------------------------------------

	/** @param status computed from the dates on today's business date; never stored */
	public record EngagementDto(Long id, ClientRef client, String name, String location, LocalDate startDate,
			LocalDate endDate, PeriodStatus status, UserSummary accountManager, String notes, Integer version,
			Instant createdAt, Instant updatedAt) {

	}

	public record SaveEngagement(@NotNull Long clientId, @NotBlank @Size(max = 200) String name,
			@Size(max = 150) String location, @NotNull LocalDate startDate, LocalDate endDate, Long accountManagerId,
			@Size(max = 5000) String notes, Integer version) {

	}

	// --- allocations ------------------------------------------------------------------------------------------

	/** @param fullName null while the consultant's employee record is in the recycle bin */
	public record ConsultantRef(Long id, String employeeCode, String fullName, UserSummary user) {

		public static ConsultantRef of(Consultant consultant) {
			Employee employee = consultant.getEmployee();
			return new ConsultantRef(consultant.getId(), employee == null ? null : employee.getEmployeeCode(),
					employee == null ? null : employee.getFullName(),
					employee == null ? null : UserSummary.of(employee.getUser()));
		}

	}

	public record EngagementRef(Long id, String name, ClientRef client) {

		public static EngagementRef of(ClientEngagement engagement) {
			return new EngagementRef(engagement.getId(), engagement.getName(), ClientRef.of(engagement.getClient()));
		}

	}

	/**
	 * @param status from the dates on today's business date; never stored
	 * @param daysUntilEnd days from today to the end date, while it has not ended; null when open-ended or ended
	 */
	public record AllocationDto(Long id, ConsultantRef consultant, EngagementRef engagement, String role,
			LocalDate startDate, LocalDate endDate, int allocationPercent, boolean billable, PeriodStatus status,
			Long daysUntilEnd, String notes, Integer version, Instant createdAt, Instant updatedAt) {

	}

	/** {@code billable} defaults to true; {@code version} is required for an update. */
	public record SaveAllocation(@NotNull Long consultantId, @NotNull Long engagementId,
			@NotBlank @Size(max = 150) String role, @NotNull LocalDate startDate, LocalDate endDate,
			@NotNull @Min(1) @Max(100) Integer allocationPercent, Boolean billable, @Size(max = 5000) String notes,
			Integer version) {

	}

	/** A stretch where the consultant's total allocation goes above 100%; {@code to} is null when open-ended. */
	public record OverAllocation(LocalDate from, LocalDate to, int totalPercent) {

	}

	/**
	 * A saved allocation and the stretches where it takes the consultant above 100% (a warning: the allocation is
	 * saved).
	 */
	public record AllocationSaved(AllocationDto allocation, List<OverAllocation> overAllocations) {

	}

	public record AllocationCheck(List<OverAllocation> overAllocations) {

	}

	// --- bench and dashboard ----------------------------------------------------------------------------------

	/**
	 * An active consultant below 100% today. Computed, never stored.
	 * @param benchPercent the unallocated share today (100 - allocated, at least 0)
	 * @param benchSince the day after they were last at 100% or more, not before their availability date
	 */
	public record BenchEntry(Long consultantId, ConsultantEmployee employee, String location, List<String> skills,
			LocalDate availableFrom, ConsultantStatus status, int allocatedPercent, int benchPercent,
			LocalDate benchSince, long daysOnBench) {

	}

	public record StatusCount(ConsultantStatus status, long count) {

	}

	/** @param consultants consultants on the bench with this skill */
	public record SkillCount(String skill, long consultants) {

	}

	/**
	 * Bench Management on today's business date. FTE: one consultant at 100% is 1.00; each consultant counts at most
	 * once (over-allocation is capped at 100%). Capacity is every active consultant available today (or already
	 * allocated). Percentages are null when there is no capacity.
	 */
	public record BenchDashboard(LocalDate asOf, List<StatusCount> byStatus, long capacityConsultants,
			BigDecimal capacityFte, BigDecimal allocatedFte, BigDecimal billableFte, BigDecimal nonBillableFte,
			BigDecimal benchFte, BigDecimal utilisationPercent, BigDecimal billablePercent,
			BigDecimal nonBillablePercent, long onBenchNow, long unallocated, BigDecimal averageDaysOnBench,
			long longestDaysOnBench, List<BenchEntry> longestOnBench, long upcomingRollOffCount,
			List<AllocationDto> upcomingRollOffs, List<SkillCount> benchSkills) {

	}

}
