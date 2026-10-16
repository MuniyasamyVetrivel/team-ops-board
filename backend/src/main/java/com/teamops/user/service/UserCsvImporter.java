package com.teamops.user.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.csv.ImportDtos.ResultFile;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentMember;
import com.teamops.department.entity.DepartmentMemberRole;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentMemberRepository;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.dto.CreateUserRequest;
import com.teamops.user.entity.PermissionCodes;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;
import com.teamops.user.repository.PermissionRepository;
import com.teamops.user.repository.RoleRepository;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Admin → Users → Import CSV. Each row becomes a user through {@link UserService#create}, so every rule of a manual
 * create applies (no privilege escalation, marketing permission rules), and every new user gets a random temporary
 * password they must change at first sign-in. The passwords are returned once, in the result file, and never stored
 * in plain text. An email that already exists is an error: nobody is overwritten.
 */
@Component
@RequiredArgsConstructor
public class UserCsvImporter implements CsvImporter<UserCsvImporter.Row> {

	public static final String TYPE = "users";

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("employee_id", "Employee ID, unique; " + EmployeeIds.DESCRIPTION, "E-1042"),
			CsvColumn.required("first_name", "First name", "Asha"),
			CsvColumn.optional("last_name", "Last name", "Iyer"),
			CsvColumn.required("email", "Work email; also the sign-in name", "asha.iyer@example.com"),
			CsvColumn.optional("phone", "Phone number", "+919800000000"),
			CsvColumn.optional("job_title", "Job title", "Recruiter"),
			CsvColumn.required("department_code", "Primary department code, e.g. IT, HR, TA, WEBDEV, DM", "TA"),
			CsvColumn.optional("extra_department_codes", "Other departments they belong to, comma separated", "HR"),
			CsvColumn.required("role", "SUPER_ADMIN, DEPARTMENT_MANAGER or EMPLOYEE", "EMPLOYEE"),
			CsvColumn.optional("reports_to_email",
					"Email of their manager: an existing user or a person on an earlier row", "manager@example.com"),
			CsvColumn.optional("location", "Office or city", "Chennai"),
			CsvColumn.optional("time_zone", "IANA time zone", "Asia/Kolkata"),
			CsvColumn.optional("extra_permissions",
					"Permissions on top of the role, comma separated, e.g. MARKETING_VIEW,LEAD_EDIT", ""));

	private static final List<String> RESULT_HEADER = List.of("employee_id", "email", "name", "temporary_password");

	private final UserService userService;

	private final UserRepository userRepository;

	private final RoleRepository roleRepository;

	private final PermissionRepository permissionRepository;

	private final DepartmentRepository departmentRepository;

	private final DepartmentMemberRepository departmentMemberRepository;

	private final AccessScopeService accessScopeService;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	public record Row(String employeeId, String email, String firstName, String lastName, String phone, String jobTitle,
			Long departmentId, Set<Long> extraDepartmentIds, String role, String reportsToEmail, String location,
			String timeZone, Set<String> permissions) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public Area area() {
		return Area.USERS;
	}

	@Override
	public String label() {
		return "Users";
	}

	@Override
	public String description() {
		return "People to add. Each gets a temporary password, shown once after the import, that they must change at "
				+ "first sign-in. Emails that already exist are rejected.";
	}

	@Override
	public String permission() {
		return PermissionCodes.USER_MANAGE;
	}

	@Override
	public List<CsvColumn> columns() {
		return COLUMNS;
	}

	@Override
	public ImportSession<Row> open(AuthenticatedUser actor) {
		Map<String, Department> departments = new HashMap<>();
		departmentRepository.findAll().forEach(d -> departments.put(d.getCode().toUpperCase(Locale.ROOT), d));
		Set<String> roles = roleRepository.findAll()
			.stream()
			.map(Role::getCode)
			.collect(Collectors.toCollection(TreeSet::new));
		Set<String> permissions = new HashSet<>(permissionRepository.findAllCodes());
		// Emails of earlier rows: all of them, and those that were valid (and will be created before later rows).
		Set<String> emailsInFile = new HashSet<>();
		Set<String> validEmailsInFile = new HashSet<>();
		Set<String> employeeIdsInFile = new HashSet<>();
		List<String[]> created = new ArrayList<>();
		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String employeeId = row.text("employee_id", EmployeeIds.MAX_LENGTH);
				String firstName = row.text("first_name", 100);
				String lastName = row.text("last_name", 100);
				String email = row.email("email");
				String phone = row.text("phone", 40);
				String jobTitle = row.text("job_title", 150);
				String departmentCode = row.text("department_code", 50);
				String extraDepartmentCodes = row.text("extra_department_codes", 500);
				String role = upper(row.text("role", 50));
				String reportsTo = row.email("reports_to_email");
				String location = row.text("location", 150);
				String timeZone = row.text("time_zone", 64);
				String extraPermissions = row.text("extra_permissions", 2000);

				if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
					row.error("email", "A user with this email already exists");
				}
				if (employeeId != null) {
					try {
						employeeId = EmployeeIds.normalise(employeeId);
						String key = employeeId.toUpperCase(Locale.ROOT);
						if (userRepository.existsByEmployeeIdIgnoreCase(employeeId)) {
							row.error("employee_id", "Another user already has this employee ID");
						}
						else if (!employeeIdsInFile.add(key)) {
							row.error("employee_id", "This employee ID is on an earlier row too");
						}
					}
					catch (ApiException ex) {
						row.error("employee_id", ex.getMessage());
					}
				}
				Department department = department(row, "department_code", departmentCode);
				Set<Long> extraDepartmentIds = new LinkedHashSet<>();
				for (String code : codes(extraDepartmentCodes)) {
					Department extra = department(row, "extra_department_codes", code);
					if (extra == null) {
						continue;
					}
					if (department != null && extra.getId().equals(department.getId())) {
						row.error("extra_department_codes", code + " is already the primary department");
					}
					else if (!accessScopeService.canManageDepartment(actor, extra.getId())) {
						row.error("extra_department_codes", "You cannot add people to " + code);
					}
					else {
						extraDepartmentIds.add(extra.getId());
					}
				}
				if (role != null && !roles.contains(role)) {
					row.error("role", "Must be one of: " + String.join(", ", roles));
				}
				Set<String> grants = new TreeSet<>(codes(extraPermissions));
				Set<String> unknown = grants.stream().filter(code -> !permissions.contains(code))
					.collect(Collectors.toCollection(TreeSet::new));
				if (!unknown.isEmpty()) {
					row.error("extra_permissions", "Unknown permissions: " + String.join(", ", unknown));
				}
				if (reportsTo != null) {
					if (reportsTo.equals(email)) {
						row.error("reports_to_email", "A person cannot report to themselves");
					}
					else if (emailsInFile.contains(reportsTo) && !validEmailsInFile.contains(reportsTo)) {
						row.error("reports_to_email", "Reports to a person on an earlier row that has errors");
					}
					else if (!validEmailsInFile.contains(reportsTo) && !userRepository.existsByEmailIgnoreCase(reportsTo)) {
						row.error("reports_to_email",
								"No user has this email. Add the manager first, or on an earlier row of this file");
					}
				}
				String zone = null;
				try {
					zone = UserService.timeZone(timeZone);
				}
				catch (ApiException ex) {
					row.error("time_zone", ex.getMessage());
				}
				if (row.valid()) {
					try {
						userService.assertCanAssign(actor, Set.of(role), grants);
					}
					catch (ApiException ex) {
						row.error(grants.isEmpty() ? "role" : "extra_permissions", ex.getMessage());
					}
				}
				if (email != null) {
					emailsInFile.add(email);
				}
				if (!row.valid()) {
					return null;
				}
				validEmailsInFile.add(email);
				return new Row(employeeId, email, firstName, lastName, phone, jobTitle, department.getId(), extraDepartmentIds,
						role, reportsTo, location, zone, grants);
			}

			private Department department(RowReader row, String column, String code) {
				if (code == null) {
					return null;
				}
				Department department = departments.get(code.toUpperCase(Locale.ROOT));
				if (department == null) {
					row.error(column, "No department has the code " + code);
					return null;
				}
				if (department.getStatus() != DepartmentStatus.ACTIVE) {
					row.error(column, "Department " + code + " is inactive");
					return null;
				}
				return department;
			}

			@Override
			public String key(Row record) {
				return record.email();
			}

			/** Rows are created in file order, so a manager on an earlier row exists before their reports. */
			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					String password = TemporaryPasswords.generate();
					Long reportsToId = record.reportsToEmail() == null ? null
							: userRepository.findByEmailIgnoreCase(record.reportsToEmail()).orElseThrow().getId();
					Long userId = userService
						.create(new CreateUserRequest(record.employeeId(), record.email(), password, record.firstName(), record.lastName(),
								record.jobTitle(), record.phone(), record.location(), null, record.timeZone(),
								record.departmentId(), reportsToId, null, Set.of(record.role()), record.permissions()),
								actor, ClientInfo.unknown())
						.id();
					User user = userRepository.getReferenceById(userId);
					for (Long departmentId : record.extraDepartmentIds()) {
						departmentMemberRepository.save(DepartmentMember.of(departmentRepository.getReferenceById(departmentId),
								user, DepartmentMemberRole.MEMBER));
					}
					created.add(new String[] { record.employeeId(), record.email(), fullName(record), password });
				}
				auditService.record(AuditAction.USERS_IMPORTED, actor.id(), "USER", null,
						Map.of("count", created.size(), "emails", created.stream().map(c -> c[1]).toList()),
						ClientInfo.unknown());
				return records.size();
			}

			@Override
			public ResultFile resultFile() {
				List<List<String>> rows = created.stream().map(Arrays::asList).toList();
				return new ResultFile("new-users-" + calendar.today() + ".csv",
						new String(CsvWriter.write(RESULT_HEADER, rows), StandardCharsets.UTF_8));
			}

		};
	}

	private static String fullName(Row record) {
		return (record.firstName() + " " + (record.lastName() == null ? "" : record.lastName())).trim();
	}

	private static String upper(String value) {
		return value == null ? null : value.toUpperCase(Locale.ROOT);
	}

	/** Codes separated by commas, semicolons or spaces, upper-cased, without blanks or repeats. */
	static Set<String> codes(String value) {
		if (value == null) {
			return Set.of();
		}
		return Arrays.stream(value.split("[,;\\s]+"))
			.filter(code -> !code.isBlank())
			.map(code -> code.toUpperCase(Locale.ROOT))
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

}
