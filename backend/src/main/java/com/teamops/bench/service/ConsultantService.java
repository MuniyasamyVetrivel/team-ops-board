package com.teamops.bench.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.bench.dto.BenchDtos.CertificationDto;
import com.teamops.bench.dto.BenchDtos.ConsultantDto;
import com.teamops.bench.dto.BenchDtos.ConsultantEmployee;
import com.teamops.bench.dto.BenchDtos.SaveCertification;
import com.teamops.bench.dto.BenchDtos.SaveConsultant;
import com.teamops.bench.entity.Allocation;
import com.teamops.bench.entity.Consultant;
import com.teamops.bench.entity.ConsultantCertification;
import com.teamops.bench.entity.ConsultantSkill;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.repository.AllocationRepository;
import com.teamops.bench.repository.ConsultantRepository;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.EmployeeStatus;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.recyclebin.service.RecycleBinService;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * Consultants on the bench (Phase X6), each linked to one Employee record. BENCH_VIEW reads, BENCH_EDIT changes (the
 * controller and the module switch check both). Changes are audited; a delete moves the consultant, with its skills
 * and certifications, to the recycle bin.
 */
@Service
@RequiredArgsConstructor
public class ConsultantService {

	public static final String ENTITY = "CONSULTANT";

	public static final int EXPORT_LIMIT = 10_000;

	/** The export's columns: the same as the import's, so an export can be edited and imported elsewhere. */
	public static final List<String> CSV_COLUMNS = List.of("employee_id", "experience_years", "location",
			"available_from", "employment_status", "skills", "certifications", "notes");

	private static final int CANDIDATE_LIMIT = 20;

	private final ConsultantRepository repository;

	private final EmployeeRepository employees;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	private final RecycleBinService recycleBin;

	private final AllocationRepository allocations;

	/** @param skill exact skill name, ignoring case */
	public record ConsultantFilter(String search, ConsultantStatus status, String skill) {
	}

	@Transactional(readOnly = true)
	public PageResponse<ConsultantDto> search(ConsultantFilter filter, Pageable pageable) {
		LocalDate today = calendar.today();
		Page<Consultant> page = repository.findAll(spec(filter, today), pageable);
		Map<Long, Integer> allocated = allocatedOn(page.map(Consultant::getId).getContent(), today);
		return PageResponse.of(page.map(c -> toDto(c, today, allocated.getOrDefault(c.getId(), 0))));
	}

	@Transactional(readOnly = true)
	public ConsultantDto get(Long id) {
		return toDto(load(id));
	}

	/** Each consultant's total allocation on the day (consultants without one are left out). */
	@Transactional(readOnly = true)
	public Map<Long, Integer> allocatedOn(Collection<Long> consultantIds, LocalDate day) {
		Map<Long, Integer> totals = new HashMap<>();
		if (!consultantIds.isEmpty()) {
			allocations.totalsOn(consultantIds, day)
				.forEach(total -> totals.put(total.getConsultantId(), Math.toIntExact(total.getTotal())));
		}
		return totals;
	}

	/** Active employees who are not consultants yet, for the "add consultant" picker. */
	@Transactional(readOnly = true)
	public List<ConsultantEmployee> candidates(String search) {
		Specification<Employee> spec = (root, query, cb) -> {
			Subquery<Long> consultant = query.subquery(Long.class);
			Root<Consultant> c = consultant.from(Consultant.class);
			consultant.select(c.get("employeeId")).where(cb.equal(c.get("employeeId"), root.get("id")));
			return cb.and(cb.equal(root.get("status"), EmployeeStatus.ACTIVE), cb.not(cb.exists(consultant)));
		};
		if (BenchText.trimToNull(search) != null) {
			String pattern = BenchText.containsPattern(search);
			spec = spec.and((root, query, cb) -> cb.or(
					cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern, '\\'),
					cb.like(cb.lower(root.get("employeeCode")), pattern, '\\')));
		}
		return employees.findAll(spec, PageRequest.of(0, CANDIDATE_LIMIT, Sort.by("firstName", "lastName", "id")))
			.map(ConsultantEmployee::of)
			.getContent();
	}

	/** The consultant list as CSV in the import's columns (certifications by name only). */
	@Transactional(readOnly = true)
	public byte[] export(ConsultantFilter filter) {
		List<List<String>> rows = repository
			.findAll(spec(filter, calendar.today()), Sort.by("employee.firstName", "employee.lastName", "id"))
			.stream()
			.limit(EXPORT_LIMIT)
			.map(c -> List.of(c.getEmployee() == null ? "" : c.getEmployee().getEmployeeCode(),
					text(c.getExperienceYears()), text(c.getLocation()), text(c.getAvailableFrom()),
					c.getEmploymentStatus().name(), BenchText.joinList(skillsOf(c)),
					BenchText.joinList(c.getCertifications().stream().map(ConsultantCertification::getName).toList()),
					text(c.getNotes())))
			.toList();
		return CsvWriter.write(CSV_COLUMNS, rows);
	}

	@Transactional
	public ConsultantDto create(SaveConsultant request, AuthenticatedUser actor, ClientInfo client) {
		Consultant consultant = new Consultant();
		apply(consultant, request);
		Consultant saved = repository.saveAndFlush(consultant);
		auditService.record(AuditAction.CONSULTANT_CREATED, actor.id(), ENTITY, saved.getId(), identity(saved), client);
		return toDto(saved);
	}

	@Transactional
	public ConsultantDto update(Long id, SaveConsultant request, AuthenticatedUser actor, ClientInfo client) {
		Consultant consultant = load(id);
		if (request.version() == null || !Objects.equals(consultant.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this consultant just now. Reload and try again.");
		}
		Snapshot before = Snapshot.of(consultant);
		apply(consultant, request);
		AuditChanges changes = new AuditChanges();
		before.compare(Snapshot.of(consultant), changes);
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.CONSULTANT_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), client);
		}
		return toDto(consultant);
	}

	/**
	 * Moves the consultant to the recycle bin; skills and certifications stay with it. 409 CONSULTANT_HAS_ALLOCATIONS
	 * while it has live allocations. Audited.
	 */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		Consultant consultant = load(id);
		if (allocations.existsByConsultantId(id)) {
			throw ApiException.conflict("CONSULTANT_HAS_ALLOCATIONS",
					"This consultant still has allocations. Delete them first.");
		}
		recycleBin.moveToBin(consultant, actor);
		auditService.record(AuditAction.CONSULTANT_DELETED, actor.id(), ENTITY, id, identity(consultant), client);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private void apply(Consultant consultant, SaveConsultant request) {
		Employee employee = employees.findDetailedById(request.employeeId()).orElse(null);
		if (employee == null) {
			if (Objects.equals(consultant.getEmployeeId(), request.employeeId())) {
				throw ApiException.conflict("EMPLOYEE_IN_RECYCLE_BIN",
						"This consultant's employee record is in the recycle bin. Restore the employee first.");
			}
			throw ApiException.badRequest("UNKNOWN_EMPLOYEE", "Employee not found");
		}
		if (!Objects.equals(consultant.getEmployeeId(), employee.getId())) {
			boolean taken = consultant.getId() == null ? repository.existsByEmployeeId(employee.getId())
					: repository.existsByEmployeeIdAndIdNot(employee.getId(), consultant.getId());
			if (taken) {
				throw ApiException.conflict("CONSULTANT_EXISTS",
						employee.getFullName() + " (" + employee.getEmployeeCode() + ") is already a consultant");
			}
			recycleBin.requireNotInBin(Consultant.class, Map.of("employee_id", employee.getId()),
					"The employee " + employee.getEmployeeCode());
		}
		List<String> skills = BenchText.skills(request.skills());
		List<SaveCertification> certifications = request.certifications() == null ? List.of() : request.certifications();
		LocalDate today = calendar.today();
		for (SaveCertification certification : certifications) {
			if (certification.issuedOn() != null && certification.issuedOn().isAfter(today)) {
				throw ApiException.badRequest("INVALID_CERTIFICATION_DATES",
						"The issue date of " + certification.name().trim() + " cannot be in the future");
			}
			if (certification.issuedOn() != null && certification.expiresOn() != null
					&& certification.expiresOn().isBefore(certification.issuedOn())) {
				throw ApiException.badRequest("INVALID_CERTIFICATION_DATES",
						"The expiry date of " + certification.name().trim() + " must be on or after its issue date");
			}
		}

		consultant.setEmployeeId(employee.getId());
		consultant.setEmployee(employee);
		consultant.setExperienceYears(request.experienceYears() == null ? null
				: request.experienceYears().setScale(1, RoundingMode.UNNECESSARY));
		consultant.setLocation(BenchText.trimToNull(request.location()));
		consultant.setAvailableFrom(request.availableFrom());
		consultant.setEmploymentStatus(
				request.employmentStatus() == null ? EmploymentStatus.ACTIVE : request.employmentStatus());
		consultant.setNotes(BenchText.trimToNull(request.notes()));
		replaceSkills(consultant, skills);
		replaceCertifications(consultant, certifications);
	}

	/**
	 * Keeps the skills whose name is still there (a change of case renames it), removes the rest and adds the new
	 * ones, so the per-consultant unique name is never taken twice during the flush.
	 */
	static void replaceSkills(Consultant consultant, List<String> names) {
		Map<String, ConsultantSkill> existing = new HashMap<>();
		consultant.getSkills().forEach(skill -> existing.put(BenchText.key(skill.getName()), skill));
		List<ConsultantSkill> next = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			ConsultantSkill skill = existing.remove(BenchText.key(names.get(i)));
			if (skill == null) {
				skill = new ConsultantSkill();
				skill.setConsultant(consultant);
			}
			skill.setName(names.get(i));
			skill.setPosition(i);
			next.add(skill);
		}
		consultant.getSkills().removeIf(skill -> existing.containsValue(skill));
		next.stream().filter(skill -> !consultant.getSkills().contains(skill)).forEach(consultant.getSkills()::add);
		consultant.getSkills().sort(Comparator.comparingInt(ConsultantSkill::getPosition));
	}

	/** Updates the certifications named by id, removes the ones left out and adds those without an id. */
	private static void replaceCertifications(Consultant consultant, List<SaveCertification> requested) {
		Map<Long, ConsultantCertification> existing = new HashMap<>();
		consultant.getCertifications().forEach(c -> existing.put(c.getId(), c));
		List<ConsultantCertification> next = new ArrayList<>();
		for (int i = 0; i < requested.size(); i++) {
			SaveCertification request = requested.get(i);
			ConsultantCertification certification;
			if (request.id() == null) {
				certification = new ConsultantCertification();
				certification.setConsultant(consultant);
			}
			else {
				certification = existing.get(request.id());
				if (certification == null) {
					throw ApiException.badRequest("UNKNOWN_CERTIFICATION",
							"Certification " + request.id() + " does not belong to this consultant");
				}
			}
			certification.setName(request.name().trim());
			certification.setIssuer(BenchText.trimToNull(request.issuer()));
			certification.setIssuedOn(request.issuedOn());
			certification.setExpiresOn(request.expiresOn());
			certification.setCredentialId(BenchText.trimToNull(request.credentialId()));
			certification.setPosition(i);
			next.add(certification);
		}
		consultant.getCertifications().removeIf(c -> !next.contains(c));
		next.stream().filter(c -> !consultant.getCertifications().contains(c)).forEach(consultant.getCertifications()::add);
		consultant.getCertifications().sort(Comparator.comparingInt(ConsultantCertification::getPosition));
	}

	/** The fields an update audits. */
	private record Snapshot(Map<String, Object> values) {

		static Snapshot of(Consultant c) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("employeeId", c.getEmployeeId());
			values.put("experienceYears", c.getExperienceYears());
			values.put("location", c.getLocation());
			values.put("availableFrom", c.getAvailableFrom());
			values.put("employmentStatus", c.getEmploymentStatus());
			values.put("skills", skillsOf(c));
			values.put("certifications", c.getCertifications().stream().map(Snapshot::certification).toList());
			values.put("notes", c.getNotes());
			return new Snapshot(values);
		}

		private static Map<String, Object> certification(ConsultantCertification c) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("name", c.getName());
			values.put("issuer", c.getIssuer());
			values.put("issuedOn", c.getIssuedOn());
			values.put("expiresOn", c.getExpiresOn());
			values.put("credentialId", c.getCredentialId());
			return values;
		}

		void compare(Snapshot after, AuditChanges changes) {
			values.forEach((field, from) -> changes.track(field, from, after.values.get(field)));
		}

	}

	private static Map<String, Object> identity(Consultant consultant) {
		Map<String, Object> details = new HashMap<>();
		details.put("employeeId", consultant.getEmployeeId());
		if (consultant.getEmployee() != null) {
			details.put("employeeCode", consultant.getEmployee().getEmployeeCode());
			details.put("name", consultant.getEmployee().getFullName());
		}
		return details;
	}

	private ConsultantDto toDto(Consultant consultant) {
		LocalDate today = calendar.today();
		return toDto(consultant, today,
				allocatedOn(List.of(consultant.getId()), today).getOrDefault(consultant.getId(), 0));
	}

	/** @param allocatedPercent the consultant's total allocation today */
	static ConsultantDto toDto(Consultant c, LocalDate today, int allocatedPercent) {
		List<CertificationDto> certifications = c.getCertifications()
			.stream()
			.map(cert -> new CertificationDto(cert.getId(), cert.getName(), cert.getIssuer(), cert.getIssuedOn(),
					cert.getExpiresOn(), cert.getCredentialId(),
					cert.getExpiresOn() != null && cert.getExpiresOn().isBefore(today)))
			.toList();
		return new ConsultantDto(c.getId(), c.getEmployeeId(), ConsultantEmployee.of(c.getEmployee()),
				c.getExperienceYears(), c.getLocation(), c.getAvailableFrom(), c.getEmploymentStatus(),
				ConsultantStatus.of(c.getEmploymentStatus(), allocatedPercent), allocatedPercent, skillsOf(c),
				certifications, c.getNotes(), c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
	}

	static List<String> skillsOf(Consultant consultant) {
		return consultant.getSkills().stream().map(ConsultantSkill::getName).toList();
	}

	private Consultant load(Long id) {
		return repository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("CONSULTANT_NOT_FOUND", "Consultant not found"));
	}

	/** Status filters match {@link ConsultantStatus#of} on the same day. */
	private static Specification<Consultant> spec(ConsultantFilter f, LocalDate today) {
		Specification<Consultant> spec = (root, query, cb) -> cb.conjunction();
		if (BenchText.trimToNull(f.search()) != null) {
			String pattern = BenchText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> {
				var employee = root.join("employee", JoinType.LEFT);
				Subquery<Long> skill = query.subquery(Long.class);
				Root<ConsultantSkill> s = skill.from(ConsultantSkill.class);
				skill.select(s.get("id"))
					.where(cb.equal(s.get("consultant"), root), cb.like(cb.lower(s.get("name")), pattern, '\\'));
				return cb.or(
						cb.like(cb.lower(cb.concat(cb.concat(employee.get("firstName"), " "), employee.get("lastName"))),
								pattern, '\\'),
						cb.like(cb.lower(employee.get("employeeCode")), pattern, '\\'),
						cb.like(cb.lower(root.get("location")), pattern, '\\'), cb.exists(skill));
			});
		}
		if (f.status() != null) {
			EmploymentStatus employment = f.status().employmentStatus();
			if (employment != null) {
				spec = spec.and((root, query, cb) -> cb.equal(root.get("employmentStatus"), employment));
			}
			else {
				ConsultantStatus status = f.status();
				spec = spec.and((root, query, cb) -> {
					Subquery<Integer> allocated = query.subquery(Integer.class);
					Root<Allocation> a = allocated.from(Allocation.class);
					allocated.select(cb.coalesce(cb.sum(a.get("allocationPercent")), 0))
						.where(cb.equal(a.get("consultant"), root), cb.lessThanOrEqualTo(a.get("startDate"), today),
								cb.or(cb.isNull(a.get("endDate")), cb.greaterThanOrEqualTo(a.get("endDate"), today)));
					return cb.and(cb.equal(root.get("employmentStatus"), EmploymentStatus.ACTIVE), switch (status) {
						case ALLOCATED -> cb.greaterThanOrEqualTo(allocated, BenchMath.FULL);
						case PARTIALLY_ALLOCATED -> cb.and(cb.greaterThan(allocated, 0), cb.lessThan(allocated, BenchMath.FULL));
						default -> cb.equal(allocated, 0);
					});
				});
			}
		}
		if (BenchText.trimToNull(f.skill()) != null) {
			String wanted = f.skill().trim().toLowerCase(Locale.ROOT);
			spec = spec.and((root, query, cb) -> {
				Subquery<Long> skill = query.subquery(Long.class);
				Root<ConsultantSkill> s = skill.from(ConsultantSkill.class);
				skill.select(s.get("id")).where(cb.equal(s.get("consultant"), root), cb.equal(cb.lower(s.get("name")), wanted));
				return cb.exists(skill);
			});
		}
		return spec;
	}

	private static String text(Object value) {
		if (value instanceof BigDecimal number) {
			return number.toPlainString();
		}
		return value == null ? "" : value.toString();
	}

}
