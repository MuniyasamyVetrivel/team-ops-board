package com.teamops.bench.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.bench.entity.Consultant;
import com.teamops.bench.entity.ConsultantCertification;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.repository.ConsultantRepository;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.user.entity.PermissionCodes;

import lombok.RequiredArgsConstructor;

/**
 * Imports consultants, e.g. an HR export; the columns match the consultant export. Each row names an existing Employee
 * record by employee ID. An unknown employee ID, or an employee who already is a consultant (in the recycle bin too),
 * is an error: nothing is overwritten. Certifications are imported by name; dates and issuers are added in the app.
 */
@Component
@RequiredArgsConstructor
public class ConsultantCsvImporter implements CsvImporter<ConsultantCsvImporter.Row> {

	public static final String TYPE = "consultants";

	private static final BigDecimal MAX_EXPERIENCE = new BigDecimal("60");

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("employee_id", "Employee ID of an existing employee record", "TRI-0042"),
			CsvColumn.optional("experience_years", "Total years of experience, up to one decimal", "6.5"),
			CsvColumn.optional("location", "Where the consultant is based", "Chennai"),
			CsvColumn.optional("available_from", "YYYY-MM-DD", "2026-11-01"),
			CsvColumn.optional("employment_status", "ACTIVE (default), LEAVE_OF_ABSENCE, NOTICE_PERIOD or LEFT",
					"ACTIVE"),
			CsvColumn.optional("skills", "Separated by semicolons", "SAP S/4HANA; Tosca; Test automation"),
			CsvColumn.optional("certifications", "Names, separated by semicolons", "ISTQB Foundation; Tosca TA1"),
			CsvColumn.optional("notes", "Anything else", "Prefers on-site work"));

	private final ConsultantRepository consultants;

	private final EmployeeRepository employees;

	private final RecycleBinService recycleBin;

	public record Row(String code, Long employeeId, BigDecimal experienceYears, String location,
			LocalDate availableFrom, EmploymentStatus employmentStatus, List<String> skills,
			List<String> certifications, String notes) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public Area area() {
		return Area.BENCH;
	}

	@Override
	public String label() {
		return "Consultants";
	}

	@Override
	public String description() {
		return "Consultants on the bench, by employee ID. Rows for an employee who already is a consultant are rejected.";
	}

	@Override
	public String permission() {
		return PermissionCodes.BENCH_EDIT;
	}

	@Override
	public List<CsvColumn> columns() {
		return COLUMNS;
	}

	@Override
	public ImportSession<Row> open(AuthenticatedUser actor) {
		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String code = row.text("employee_id", 40);
				BigDecimal experience = row.amount("experience_years", 1);
				String location = row.text("location", 150);
				LocalDate availableFrom = row.date("available_from");
				EmploymentStatus status = row.choice("employment_status", EmploymentStatus.class);
				List<String> skills = list(row, "skills", BenchText.MAX_SKILLS, BenchText.SKILL_LENGTH);
				List<String> certifications = list(row, "certifications", BenchText.MAX_CERTIFICATIONS,
						BenchText.CERTIFICATION_LENGTH);
				String notes = row.text("notes", 5000);
				if (experience != null && experience.compareTo(MAX_EXPERIENCE) > 0) {
					row.error("experience_years", "Must be at most 60");
				}
				if (!row.valid()) {
					return null;
				}
				Employee employee = employees.findByEmployeeCode(code).orElse(null);
				if (employee == null) {
					row.error("employee_id", "No employee record has this ID");
					return null;
				}
				if (consultants.existsByEmployeeId(employee.getId())) {
					row.error("employee_id", "This employee already is a consultant");
					return null;
				}
				try {
					recycleBin.requireNotInBin(Consultant.class, Map.of("employee_id", employee.getId()),
							"This employee");
				}
				catch (ApiException ex) {
					row.error("employee_id", ex.getMessage());
					return null;
				}
				return new Row(employee.getEmployeeCode(), employee.getId(),
						experience == null ? null : experience.setScale(1, RoundingMode.UNNECESSARY), location,
						availableFrom, status == null ? EmploymentStatus.ACTIVE : status, skills, certifications, notes);
			}

			@Override
			public String key(Row record) {
				return record.code().toLowerCase(Locale.ROOT);
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					Consultant consultant = new Consultant();
					consultant.setEmployeeId(record.employeeId());
					consultant.setExperienceYears(record.experienceYears());
					consultant.setLocation(record.location());
					consultant.setAvailableFrom(record.availableFrom());
					consultant.setEmploymentStatus(record.employmentStatus());
					consultant.setNotes(record.notes());
					ConsultantService.replaceSkills(consultant, record.skills());
					for (int i = 0; i < record.certifications().size(); i++) {
						ConsultantCertification certification = new ConsultantCertification();
						certification.setConsultant(consultant);
						certification.setName(record.certifications().get(i));
						certification.setPosition(i);
						consultant.getCertifications().add(certification);
					}
					consultants.save(consultant);
				}
				return records.size();
			}

		};
	}

	/** A semicolon-separated cell: at most {@code max} entries of at most {@code length} characters each. */
	private static List<String> list(RowReader row, String column, int max, int length) {
		String cell = row.text(column, max * (length + 2));
		List<String> values = BenchText.splitList(cell);
		if (values.size() > max) {
			row.error(column, "At most " + max + " entries");
		}
		values.stream()
			.filter(value -> value.length() > length)
			.findFirst()
			.ifPresent(value -> row.error(column, "Each entry must be at most " + length + " characters"));
		return values;
	}

}
