package com.teamops.celebration.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.celebration.dto.CelebrationDtos.EmployeeDto;
import com.teamops.celebration.dto.CelebrationDtos.SaveEmployee;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.EmployeeStatus;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.celebration.service.OccasionAttachmentService.Download;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.common.storage.StoredFileRepository;
import com.teamops.common.storage.UploadPolicy;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Employees whose birthdays and work anniversaries are celebrated. Everyone signed in can list active employees and
 * see the day and month of their birthdays; full birth dates, people who left and every change are for Super Admins
 * and CELEBRATIONS_EDIT holders. Changes are audited.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

	public static final String ENTITY = "EMPLOYEE";

	public static final int EXPORT_LIMIT = 10_000;

	/** The export's columns: the same as the import's, so an export can be edited and imported elsewhere. */
	public static final List<String> CSV_COLUMNS = List.of("employee_id", "first_name", "last_name", "date_of_birth",
			"designation", "reporting_manager", "date_of_joining", "department_code", "work_email", "office_location",
			"status", "user_email");

	private static final Set<OccasionKind> OWN_KINDS = Set.of(OccasionKind.BIRTHDAY, OccasionKind.WORK_ANNIVERSARY);

	private final EmployeeRepository repository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final EmployeeUserLinks userLinks;

	private final StoredFileRepository storedFiles;

	private final FileService fileService;

	private final LinkedInPostService linkedInPosts;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	private final RecycleBinService recycleBin;

	public record EmployeeFilter(String search, EmployeeStatus status, Long departmentId) {
	}

	@Transactional(readOnly = true)
	public PageResponse<EmployeeDto> search(EmployeeFilter filter, Pageable pageable, AuthenticatedUser viewer) {
		LocalDate today = calendar.today();
		return PageResponse.of(repository.findAll(spec(visibleFilter(filter, viewer)), pageable)
			.map(employee -> toDto(employee, viewer, today)));
	}

	@Transactional(readOnly = true)
	public EmployeeDto get(Long id, AuthenticatedUser viewer) {
		return toDto(loadVisible(id, viewer), viewer, calendar.today());
	}

	/** The employee list as CSV in the import's columns (CELEBRATIONS_EDIT: it holds full birth dates). */
	@Transactional(readOnly = true)
	public byte[] export(EmployeeFilter filter) {
		List<List<String>> rows = repository
			.findAll(spec(filter), Sort.by("firstName", "lastName", "id"))
			.stream()
			.limit(EXPORT_LIMIT)
			.map(e -> List.of(e.getEmployeeCode(), e.getFirstName(), e.getLastName(), e.getDateOfBirth().toString(),
					e.getDesignation(), e.getReportingManager(), text(e.getDateOfJoining()),
					e.getDepartment() == null ? "" : e.getDepartment().getCode(), text(e.getWorkEmail()),
					text(e.getOfficeLocation()), e.getStatus().name(), e.getUser() == null ? "" : e.getUser().getEmail()))
			.toList();
		return CsvWriter.write(CSV_COLUMNS, rows);
	}

	@Transactional
	public EmployeeDto create(SaveEmployee request, AuthenticatedUser actor, ClientInfo client) {
		Employee employee = new Employee();
		employee.setCreatedBy(userRepository.getReferenceById(actor.id()));
		apply(employee, request);
		Employee saved = repository.saveAndFlush(employee);
		Map<String, Object> details = new HashMap<>();
		details.put("employeeCode", saved.getEmployeeCode());
		details.put("name", saved.getFullName());
		auditService.record(AuditAction.EMPLOYEE_CREATED, actor.id(), ENTITY, saved.getId(), details, client);
		return toDto(saved, actor, calendar.today());
	}

	@Transactional
	public EmployeeDto update(Long id, SaveEmployee request, AuthenticatedUser actor, ClientInfo client) {
		Employee employee = load(id);
		if (request.version() == null || !Objects.equals(employee.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this employee just now. Reload and try again.");
		}
		LocalDate bornBefore = employee.getDateOfBirth();
		LocalDate joinedBefore = employee.getDateOfJoining();
		AuditChanges changes = new AuditChanges();
		Snapshot before = Snapshot.of(employee);
		apply(employee, request);
		before.compare(Snapshot.of(employee), changes);
		repository.flush();

		LocalDate today = calendar.today();
		if (!bornBefore.equals(employee.getDateOfBirth())) {
			LocalDate born = employee.getDateOfBirth();
			linkedInPosts.relocate(OccasionKind.BIRTHDAY, id, today, date -> AnnualDates.in(born, date.getYear()));
		}
		if (!Objects.equals(joinedBefore, employee.getDateOfJoining())) {
			LocalDate joined = employee.getDateOfJoining();
			linkedInPosts.relocate(OccasionKind.WORK_ANNIVERSARY, id, today, date -> joined == null
					|| date.getYear() < AnnualDates.firstAnniversaryYear(joined) ? null : AnnualDates.in(joined, date.getYear()));
		}
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.EMPLOYEE_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), client);
		}
		return toDto(employee, actor, today);
	}

	/** Deletes the employee with their photo and LinkedIn post tracking. Audited. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		Employee employee = load(id);
		// The photo and LinkedIn posts stay with the record in the recycle bin (EmployeeRecycleHandler).
		recycleBin.moveToBin(employee, actor);
		Map<String, Object> details = new HashMap<>();
		details.put("employeeCode", employee.getEmployeeCode());
		details.put("name", employee.getFullName());
		auditService.record(AuditAction.EMPLOYEE_DELETED, actor.id(), ENTITY, id, details, client);
	}

	// --- photo ------------------------------------------------------------------------------------------------

	/** Replaces the photo: an image (png, jpg, gif or webp), checked and stored by {@link FileService}. Audited. */
	@Transactional
	public EmployeeDto setPhoto(Long id, MultipartFile upload, AuthenticatedUser actor, ClientInfo client) {
		Employee employee = load(id);
		UploadPolicy.Checked checked = UploadPolicy.check(upload.getOriginalFilename(), Math.max(upload.getSize(), 1),
				Long.MAX_VALUE);
		if (!checked.contentType().startsWith("image/")) {
			throw ApiException.badRequest("PHOTO_NOT_IMAGE", "The photo must be a PNG, JPG, GIF or WebP image");
		}
		StoredFile previous = employee.getPhoto();
		StoredFile photo = fileService.store(upload, actor.id());
		employee.setPhoto(photo);
		repository.flush();
		if (previous != null) {
			fileService.delete(previous);
		}
		auditService.record(AuditAction.EMPLOYEE_UPDATED, actor.id(), ENTITY, id,
				new AuditChanges().track("photo", previous == null ? null : previous.getOriginalName(),
						photo.getOriginalName()).toDetails(), client);
		return toDto(employee, actor, calendar.today());
	}

	@Transactional
	public EmployeeDto deletePhoto(Long id, AuthenticatedUser actor, ClientInfo client) {
		Employee employee = load(id);
		StoredFile photo = employee.getPhoto();
		if (photo != null) {
			employee.setPhoto(null);
			repository.flush();
			fileService.delete(photo);
			auditService.record(AuditAction.EMPLOYEE_UPDATED, actor.id(), ENTITY, id,
					new AuditChanges().track("photo", photo.getOriginalName(), null).toDetails(), client);
		}
		return toDto(employee, actor, calendar.today());
	}

	/**
	 * The photo of an employee the viewer can see: their own, or else the profile photo of the app user linked by
	 * employee ID (Phase X4: a person's photo is kept once). 404 {@code PHOTO_NOT_FOUND} without either.
	 */
	@Transactional(readOnly = true)
	public Download photo(Long id, AuthenticatedUser viewer) {
		Employee employee = loadVisible(id, viewer);
		StoredFile photo = employee.getPhoto();
		if (photo == null && linkedUserPhoto(employee) != null) {
			photo = storedFiles.findById(linkedUserPhoto(employee)).orElse(null);
		}
		if (photo == null) {
			throw ApiException.notFound("PHOTO_NOT_FOUND", "This employee has no photo");
		}
		return new Download(photo.getOriginalName(), photo.getContentType(), photo.getSizeBytes(), fileService.content(photo));
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private static Long linkedUserPhoto(Employee employee) {
		return employee.getUser() == null ? null : employee.getUser().getPhotoFileId();
	}

	/**
	 * Applies the request: the app user with the same employee ID is linked (Phase X4; an explicit {@code userId} must
	 * agree with it) and fills blank fields (names, designation, reporting manager, department, work email), then the
	 * required fields, unique employee ID and dates are checked.
	 */
	private void apply(Employee employee, SaveEmployee request) {
		String code = request.employeeCode().trim();
		User byEmployeeId = userLinks.userFor(code);
		User user = byEmployeeId;
		if (request.userId() != null) {
			user = userRepository.findWithDepartmentById(request.userId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "User not found"));
			if (!EmployeeUserLinks.matches(code, user)
					|| (byEmployeeId != null && !byEmployeeId.getId().equals(user.getId()))) {
				throw ApiException.badRequest("EMPLOYEE_ID_MISMATCH", "This app user's employee ID is "
						+ (user.getEmployeeId() == null ? "not set" : user.getEmployeeId())
						+ "; an employee is linked to the user with the same employee ID");
			}
		}
		if (user != null) {
			Employee other = repository.findByUserId(user.getId())
				.filter(linked -> !linked.getId().equals(employee.getId()))
				.orElse(null);
			if (other != null) {
				if (user != byEmployeeId) {
					throw ApiException.conflict("EMPLOYEE_USER_TAKEN",
							"This user is already linked to employee " + other.getEmployeeCode());
				}
				// Linked by hand to another record before employee IDs existed: the matching employee ID wins.
				other.setUser(null);
				repository.flush();
			}
			recycleBin.requireNotInBin(Employee.class, Map.of("user_id", user.getId()), "This user");
		}
		String firstName = fill(request.firstName(), user == null ? null : user.getFirstName());
		String lastName = fill(request.lastName(), user == null ? null : user.getLastName());
		String designation = fill(request.designation(), user == null ? null : user.getJobTitle());
		String manager = fill(request.reportingManager(),
				user == null || user.getReportsTo() == null ? null : user.getReportsTo().getFullName());
		String workEmail = fill(request.workEmail(), user == null ? null : user.getEmail());
		require(firstName, "First name");
		require(lastName, "Last name");
		require(designation, "Designation");
		require(manager, "Reporting manager");

		boolean taken = employee.getId() == null ? repository.existsByEmployeeCode(code)
				: repository.existsByEmployeeCodeAndIdNot(code, employee.getId());
		if (taken) {
			throw ApiException.conflict("EMPLOYEE_CODE_TAKEN", "Another employee already has the employee ID " + code);
		}
		recycleBin.requireNotInBin(Employee.class, Map.of("employee_code", code), "The employee ID " + code);
		LocalDate today = calendar.today();
		if (request.dateOfBirth().isAfter(today)) {
			throw ApiException.badRequest("INVALID_DATE_OF_BIRTH", "The date of birth cannot be in the future");
		}
		if (request.dateOfJoining() != null && !request.dateOfJoining().isAfter(request.dateOfBirth())) {
			throw ApiException.badRequest("INVALID_DATE_OF_JOINING", "The date of joining must be after the date of birth");
		}
		Department department = null;
		if (request.departmentId() != null) {
			department = departmentRepository.findById(request.departmentId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		}
		else if (user != null) {
			department = user.getDepartment();
		}

		employee.setEmployeeCode(code);
		employee.setFirstName(firstName);
		employee.setLastName(lastName);
		employee.setDateOfBirth(request.dateOfBirth());
		employee.setDesignation(designation);
		employee.setReportingManager(manager);
		employee.setDateOfJoining(request.dateOfJoining());
		employee.setDepartment(department);
		employee.setWorkEmail(workEmail);
		employee.setOfficeLocation(CelebrationText.trimToNull(request.officeLocation()));
		employee.setUser(user);
		employee.setStatus(request.status() == null ? EmployeeStatus.ACTIVE : request.status());
	}

	/** The fields an update audits. */
	private record Snapshot(Map<String, Object> values) {

		static Snapshot of(Employee e) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("employeeCode", e.getEmployeeCode());
			values.put("firstName", e.getFirstName());
			values.put("lastName", e.getLastName());
			values.put("dateOfBirth", e.getDateOfBirth());
			values.put("designation", e.getDesignation());
			values.put("reportingManager", e.getReportingManager());
			values.put("dateOfJoining", e.getDateOfJoining());
			values.put("departmentId", e.getDepartment() == null ? null : e.getDepartment().getId());
			values.put("workEmail", e.getWorkEmail());
			values.put("officeLocation", e.getOfficeLocation());
			values.put("userId", e.getUser() == null ? null : e.getUser().getId());
			values.put("status", e.getStatus());
			return new Snapshot(values);
		}

		void compare(Snapshot after, AuditChanges changes) {
			values.forEach((field, from) -> changes.track(field, from, after.values.get(field)));
		}

	}

	EmployeeDto toDto(Employee e, AuthenticatedUser viewer, LocalDate today) {
		LocalDate born = e.getDateOfBirth();
		LocalDate joined = e.getDateOfJoining();
		LocalDate nextAnniversary = joined == null ? null
				: AnnualDates.next(joined, AnnualDates.firstAnniversaryYear(joined), today);
		boolean full = CelebrationAccess.canSeeBirthDates(viewer);
		return new EmployeeDto(e.getId(), e.getEmployeeCode(), e.getFirstName(), e.getLastName(), e.getFullName(),
				e.getDesignation(), e.getReportingManager(),
				e.getDepartment() == null ? null : DepartmentSummary.of(e.getDepartment()), e.getWorkEmail(),
				e.getOfficeLocation(), e.getPhoto() != null || linkedUserPhoto(e) != null, UserSummary.of(e.getUser()),
				e.getStatus(),
				born.getMonthValue(), born.getDayOfMonth(), full ? born : null, joined,
				AnnualDates.next(born, AnnualDates.firstBirthdayYear(born), today), nextAnniversary,
				nextAnniversary == null ? null : AnnualDates.years(joined, nextAnniversary), e.getVersion(),
				CelebrationAccess.canEdit(viewer));
	}

	/** People who left are only listed for editors. */
	private static EmployeeFilter visibleFilter(EmployeeFilter filter, AuthenticatedUser viewer) {
		return CelebrationAccess.canEdit(viewer) ? filter
				: new EmployeeFilter(filter.search(), EmployeeStatus.ACTIVE, filter.departmentId());
	}

	private Employee loadVisible(Long id, AuthenticatedUser viewer) {
		return repository.findDetailedById(id)
			.filter(employee -> employee.isActive() || CelebrationAccess.canEdit(viewer))
			.orElseThrow(EmployeeService::notFound);
	}

	private Employee load(Long id) {
		return repository.findDetailedById(id).orElseThrow(EmployeeService::notFound);
	}

	private static ApiException notFound() {
		return ApiException.notFound("EMPLOYEE_NOT_FOUND", "Employee not found");
	}

	private static Specification<Employee> spec(EmployeeFilter f) {
		Specification<Employee> spec = (root, query, cb) -> cb.conjunction();
		if (f.search() != null && !f.search().isBlank()) {
			String pattern = CelebrationText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> cb.or(
					cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern, '\\'),
					cb.like(cb.lower(root.get("employeeCode")), pattern, '\\'),
					cb.like(cb.lower(root.get("designation")), pattern, '\\')));
		}
		if (f.status() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), f.status()));
		}
		if (f.departmentId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("department").get("id"), f.departmentId()));
		}
		return spec;
	}

	private static String fill(String value, String fallback) {
		String trimmed = CelebrationText.trimToNull(value);
		return trimmed != null ? trimmed : CelebrationText.trimToNull(fallback);
	}

	private static void require(String value, String label) {
		if (value == null) {
			throw ApiException.badRequest("FIELD_REQUIRED", label + " is required (or link an app user that has it)");
		}
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

}
