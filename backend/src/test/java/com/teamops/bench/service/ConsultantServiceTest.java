package com.teamops.bench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.bench.dto.BenchDtos.ConsultantDto;
import com.teamops.bench.dto.BenchDtos.SaveCertification;
import com.teamops.bench.dto.BenchDtos.SaveConsultant;
import com.teamops.bench.entity.Consultant;
import com.teamops.bench.entity.ConsultantCertification;
import com.teamops.bench.entity.ConsultantSkill;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.repository.AllocationRepository;
import com.teamops.bench.repository.ConsultantRepository;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.web.ClientInfo;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SliceAuth;

class ConsultantServiceTest {

	private final ConsultantRepository repository = mock(ConsultantRepository.class);

	private final EmployeeRepository employees = mock(EmployeeRepository.class);

	private final AuditService audit = mock(AuditService.class);

	private final RecycleBinService recycleBin = mock(RecycleBinService.class);

	private final AllocationRepository allocations = mock(AllocationRepository.class);

	private final ClientInfo client = new ClientInfo("127.0.0.1", "test");

	private ConsultantService service;

	private Employee priya;

	@BeforeEach
	void setUp() {
		service = new ConsultantService(repository, employees, audit,
				new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-16T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"),
				recycleBin, allocations);
		priya = employee(7L, "TRI-0042", "Priya", "Menon");
		when(employees.findDetailedById(7L)).thenReturn(Optional.of(priya));
		when(repository.saveAndFlush(any())).thenAnswer(inv -> {
			Consultant consultant = inv.getArgument(0);
			ReflectionTestUtils.setField(consultant, "id", 30L);
			consultant.setVersion(0);
			return consultant;
		});
	}

	@Test
	void aNewConsultantIsOnTheBenchWithTheirSkillsAndCertifications() {
		ConsultantDto created = service.create(new SaveConsultant(7L, new BigDecimal("6.5"), " Chennai ",
				LocalDate.of(2026, 11, 1), null, List.of("SAP S/4HANA", " Tosca", "tosca"),
				List.of(new SaveCertification(null, "ISTQB Foundation", "ISTQB", LocalDate.of(2020, 5, 1), null, null),
						new SaveCertification(null, "Tosca TA1", null, LocalDate.of(2021, 1, 1),
								LocalDate.of(2023, 1, 1), "TA1-77")),
				null, null), SliceAuth.SUPER_ADMIN, client);

		assertThat(created.employee().employeeCode()).isEqualTo("TRI-0042");
		assertThat(created.employee().fullName()).isEqualTo("Priya Menon");
		assertThat(created.location()).isEqualTo("Chennai");
		assertThat(created.experienceYears()).isEqualByComparingTo("6.5");
		assertThat(created.employmentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
		assertThat(created.status()).isEqualTo(ConsultantStatus.ON_BENCH);
		assertThat(created.skills()).containsExactly("SAP S/4HANA", "Tosca");
		assertThat(created.certifications()).extracting("name").containsExactly("ISTQB Foundation", "Tosca TA1");
		assertThat(created.certifications()).extracting("expired").containsExactly(false, true);
		verify(audit).record(eq(AuditAction.CONSULTANT_CREATED), eq(1L), eq("CONSULTANT"), eq(30L), anyMap(), eq(client));
	}

	@Test
	void anEmployeeIsAConsultantOnlyOnce() {
		when(repository.existsByEmployeeId(7L)).thenReturn(true);
		assertCode(() -> service.create(save(7L, List.of(), null), SliceAuth.SUPER_ADMIN, client), "CONSULTANT_EXISTS");

		when(repository.existsByEmployeeId(7L)).thenReturn(false);
		doThrow(ApiException.conflict("IN_RECYCLE_BIN", "in the bin")).when(recycleBin)
			.requireNotInBin(eq(Consultant.class), eq(Map.of("employee_id", 7L)), any());
		assertCode(() -> service.create(save(7L, List.of(), null), SliceAuth.SUPER_ADMIN, client), "IN_RECYCLE_BIN");

		assertCode(() -> service.create(save(99L, List.of(), null), SliceAuth.SUPER_ADMIN, client), "UNKNOWN_EMPLOYEE");
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void certificationDatesMustMakeSense() {
		SaveConsultant future = new SaveConsultant(7L, null, null, null, null, List.of(),
				List.of(new SaveCertification(null, "AWS SAA", null, LocalDate.of(2026, 10, 17), null, null)), null, null);
		assertCode(() -> service.create(future, SliceAuth.SUPER_ADMIN, client), "INVALID_CERTIFICATION_DATES");

		SaveConsultant backwards = new SaveConsultant(7L, null, null, null, null, List.of(), List.of(
				new SaveCertification(null, "AWS SAA", null, LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1), null)),
				null, null);
		assertCode(() -> service.create(backwards, SliceAuth.SUPER_ADMIN, client), "INVALID_CERTIFICATION_DATES");
	}

	@Test
	void anUpdateNeedsTheCurrentVersionAndAuditsOnlyRealChanges() {
		Consultant consultant = existing();

		assertCode(() -> service.update(30L, save(7L, List.of("Tosca"), null), SliceAuth.SUPER_ADMIN, client),
				"STALE_UPDATE");
		assertCode(() -> service.update(30L, save(7L, List.of("Tosca"), 1), SliceAuth.SUPER_ADMIN, client),
				"STALE_UPDATE");

		service.update(30L, save(7L, List.of("SAP S/4HANA", "Tosca"), 2), SliceAuth.SUPER_ADMIN, client);
		verify(audit, never()).record(eq(AuditAction.CONSULTANT_UPDATED), any(), any(), any(), any(), any());

		consultant.setVersion(2);
		service.update(30L, save(7L, List.of("Tosca", "Performance testing"), 2), SliceAuth.SUPER_ADMIN, client);
		verify(audit).record(eq(AuditAction.CONSULTANT_UPDATED), eq(1L), eq("CONSULTANT"), eq(30L),
				eq(Map.of("changes", Map.of("skills", changed(List.of("SAP S/4HANA", "Tosca"),
						List.of("Tosca", "Performance testing"))))),
				eq(client));
	}

	@Test
	void skillsKeepTheirRowsSoTheUniqueNameIsNeverTakenTwice() {
		Consultant consultant = existing();
		ConsultantSkill tosca = consultant.getSkills().get(1);

		ConsultantService.replaceSkills(consultant, List.of("TOSCA", "Test automation"));

		assertThat(consultant.getSkills()).extracting(ConsultantSkill::getName).containsExactly("TOSCA", "Test automation");
		assertThat(consultant.getSkills().get(0)).as("a change of case renames the same row").isSameAs(tosca);
		assertThat(consultant.getSkills()).extracting(ConsultantSkill::getPosition).containsExactly(0, 1);
	}

	@Test
	void certificationsAreChangedByIdAndOthersRemoved() {
		Consultant consultant = existing();
		ConsultantCertification istqb = consultant.getCertifications().get(0);
		when(repository.findDetailedById(30L)).thenReturn(Optional.of(consultant));

		ConsultantDto updated = service.update(30L, new SaveConsultant(7L, null, null, null, null, List.of(),
				List.of(new SaveCertification(null, "AWS SAA", "AWS", null, null, null),
						new SaveCertification(81L, "ISTQB Advanced", "ISTQB", null, null, null)),
				null, 2), SliceAuth.SUPER_ADMIN, client);

		assertThat(updated.certifications()).extracting("name").containsExactly("AWS SAA", "ISTQB Advanced");
		assertThat(consultant.getCertifications().get(1)).isSameAs(istqb);
		assertCode(() -> service.update(30L, new SaveConsultant(7L, null, null, null, null, List.of(),
				List.of(new SaveCertification(999L, "Unknown", null, null, null, null)), null, consultant.getVersion()),
				SliceAuth.SUPER_ADMIN, client), "UNKNOWN_CERTIFICATION");
	}

	@Test
	void deletingMovesTheConsultantToTheRecycleBin() {
		Consultant consultant = existing();

		service.delete(30L, SliceAuth.SUPER_ADMIN, client);

		verify(recycleBin).moveToBin(consultant, SliceAuth.SUPER_ADMIN);
		verify(audit).record(eq(AuditAction.CONSULTANT_DELETED), eq(1L), eq("CONSULTANT"), eq(30L), anyMap(), eq(client));
	}

	@Test
	void aConsultantWhoseEmployeeIsInTheBinStillReads() {
		Consultant consultant = existing();
		consultant.setEmployee(null);

		ConsultantDto dto = service.get(30L);

		assertThat(dto.employee()).isNull();
		assertThat(dto.employeeId()).isEqualTo(7L);
		assertCode(() -> service.get(31L), "CONSULTANT_NOT_FOUND");
	}

	private Consultant existing() {
		Consultant consultant = new Consultant();
		ReflectionTestUtils.setField(consultant, "id", 30L);
		consultant.setEmployeeId(7L);
		consultant.setEmployee(priya);
		consultant.setVersion(2);
		ConsultantService.replaceSkills(consultant, List.of("SAP S/4HANA", "Tosca"));
		ConsultantCertification istqb = new ConsultantCertification();
		ReflectionTestUtils.setField(istqb, "id", 81L);
		istqb.setConsultant(consultant);
		istqb.setName("ISTQB Foundation");
		consultant.getCertifications().add(istqb);
		when(repository.findDetailedById(30L)).thenReturn(Optional.of(consultant));
		return consultant;
	}

	private static SaveConsultant save(Long employeeId, List<String> skills, Integer version) {
		return new SaveConsultant(employeeId, null, null, null, null, skills,
				List.of(new SaveCertification(81L, "ISTQB Foundation", null, null, null, null)), null, version);
	}

	private static Map<String, Object> changed(Object from, Object to) {
		return Map.of("from", from, "to", to);
	}

	private static Employee employee(long id, String code, String firstName, String lastName) {
		Employee employee = new Employee();
		ReflectionTestUtils.setField(employee, "id", id);
		employee.setEmployeeCode(code);
		employee.setFirstName(firstName);
		employee.setLastName(lastName);
		employee.setDesignation("Test Lead");
		return employee;
	}

	private static void assertCode(ThrowingCallable call, String code) {
		assertThatThrownBy(call).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
	}

}
