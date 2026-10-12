package com.teamops.celebration.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.EmployeeStatus;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Imports employees for birthdays and work anniversaries, e.g. an HR export. The columns match the employee export.
 * An employee ID that already exists, an unknown department code, or an app user already linked to someone is an
 * error: nothing is overwritten.
 */
@Component
@RequiredArgsConstructor
public class EmployeeCsvImporter implements CsvImporter<EmployeeCsvImporter.Row> {

	public static final String TYPE = "employees";

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("employee_id", "Unique employee ID", "TRI-0042"),
			CsvColumn.required("first_name", "First name", "Priya"),
			CsvColumn.required("last_name", "Last name", "Menon"),
			CsvColumn.required("date_of_birth", "YYYY-MM-DD", "1990-10-14"),
			CsvColumn.required("designation", "Job title", "Digital Marketing Manager"),
			CsvColumn.required("reporting_manager", "Who they report to", "Rakesh"),
			CsvColumn.optional("date_of_joining", "YYYY-MM-DD; needed for work anniversaries", "2021-10-14"),
			CsvColumn.optional("department_code", "e.g. DM, IT, HR", "DM"),
			CsvColumn.optional("work_email", "Work email", "priya.menon@tritusa.com"),
			CsvColumn.optional("office_location", "Office", "Chennai"),
			CsvColumn.optional("status", "ACTIVE (default) or LEFT", "ACTIVE"),
			CsvColumn.optional("user_email", "Email of the app user to link, if they sign in", "priya.menon@teamops.local"));

	private final EmployeeRepository employeeRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final BusinessCalendar calendar;

	public record Row(String code, String firstName, String lastName, LocalDate dateOfBirth, String designation,
			String reportingManager, LocalDate dateOfJoining, Long departmentId, String workEmail,
			String officeLocation, EmployeeStatus status, Long userId) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public String label() {
		return "Employees";
	}

	@Override
	public String description() {
		return "Employees for birthdays and work anniversaries. Rows with an employee ID that already exists are rejected.";
	}

	@Override
	public String permission() {
		return CelebrationAccess.EDIT_PERMISSION;
	}

	@Override
	public List<CsvColumn> columns() {
		return COLUMNS;
	}

	@Override
	public ImportSession<Row> open(AuthenticatedUser actor) {
		Map<String, Long> departments = new HashMap<>();
		departmentRepository.findAll().forEach(d -> departments.put(d.getCode().toUpperCase(Locale.ROOT), d.getId()));
		Set<Long> linkedUsers = new HashSet<>();
		employeeRepository.findByUserIdIsNotNull().forEach(e -> linkedUsers.add(e.getUser().getId()));
		LocalDate today = calendar.today();
		Set<Long> usersInFile = new HashSet<>();
		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String code = row.text("employee_id", 40);
				String firstName = row.text("first_name", 100);
				String lastName = row.text("last_name", 100);
				LocalDate born = row.date("date_of_birth");
				String designation = row.text("designation", 150);
				String manager = row.text("reporting_manager", 150);
				LocalDate joined = row.date("date_of_joining");
				String departmentCode = row.text("department_code", 20);
				String workEmail = row.email("work_email");
				String office = row.text("office_location", 150);
				EmployeeStatus status = row.choice("status", EmployeeStatus.class);
				String userEmail = row.email("user_email");
				if (!row.valid()) {
					return null;
				}
				if (employeeRepository.existsByEmployeeCode(code)) {
					row.error("employee_id", "An employee with this ID already exists");
				}
				if (born.isAfter(today)) {
					row.error("date_of_birth", "Cannot be in the future");
				}
				if (joined != null && !joined.isAfter(born)) {
					row.error("date_of_joining", "Must be after the date of birth");
				}
				Long departmentId = null;
				if (departmentCode != null) {
					departmentId = departments.get(departmentCode.toUpperCase(Locale.ROOT));
					if (departmentId == null) {
						row.error("department_code", "No department has this code");
					}
				}
				Long userId = null;
				if (userEmail != null) {
					User user = userRepository.findByEmailIgnoreCase(userEmail).orElse(null);
					if (user == null) {
						row.error("user_email", "No app user has this email");
					}
					else if (linkedUsers.contains(user.getId())) {
						row.error("user_email", "This user is already linked to another employee");
					}
					else if (!usersInFile.add(user.getId())) {
						row.error("user_email", "Another row in this file links the same user");
					}
					else {
						userId = user.getId();
					}
				}
				return row.valid() ? new Row(code, firstName, lastName, born, designation, manager, joined, departmentId,
						workEmail, office, status == null ? EmployeeStatus.ACTIVE : status, userId) : null;
			}

			@Override
			public String key(Row record) {
				return record.code().toLowerCase(Locale.ROOT);
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					Employee employee = new Employee();
					employee.setEmployeeCode(record.code());
					employee.setFirstName(record.firstName());
					employee.setLastName(record.lastName());
					employee.setDateOfBirth(record.dateOfBirth());
					employee.setDesignation(record.designation());
					employee.setReportingManager(record.reportingManager());
					employee.setDateOfJoining(record.dateOfJoining());
					employee.setDepartment(record.departmentId() == null ? null
							: departmentRepository.getReferenceById(record.departmentId()));
					employee.setWorkEmail(record.workEmail());
					employee.setOfficeLocation(record.officeLocation());
					employee.setStatus(record.status());
					employee.setUser(record.userId() == null ? null : userRepository.getReferenceById(record.userId()));
					employee.setCreatedBy(userRepository.getReferenceById(actor.id()));
					employeeRepository.save(employee);
				}
				return records.size();
			}

		};
	}

}
