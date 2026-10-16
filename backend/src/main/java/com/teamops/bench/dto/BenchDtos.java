package com.teamops.bench.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.bench.entity.Client;
import com.teamops.bench.entity.ClientStatus;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.service.ConsultantStatus;
import com.teamops.bench.service.EngagementStatus;
import com.teamops.celebration.entity.Employee;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
	 */
	public record ConsultantDto(Long id, Long employeeId, ConsultantEmployee employee, BigDecimal experienceYears,
			String location, LocalDate availableFrom, EmploymentStatus employmentStatus, ConsultantStatus status,
			List<String> skills, List<CertificationDto> certifications, String notes, Integer version,
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
			LocalDate endDate, EngagementStatus status, UserSummary accountManager, String notes, Integer version,
			Instant createdAt, Instant updatedAt) {

	}

	public record SaveEngagement(@NotNull Long clientId, @NotBlank @Size(max = 200) String name,
			@Size(max = 150) String location, @NotNull LocalDate startDate, LocalDate endDate, Long accountManagerId,
			@Size(max = 5000) String notes, Integer version) {

	}

}
