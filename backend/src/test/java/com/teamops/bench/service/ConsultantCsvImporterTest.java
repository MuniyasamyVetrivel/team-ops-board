package com.teamops.bench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.bench.entity.Consultant;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.repository.ConsultantRepository;
import com.teamops.bench.service.ConsultantCsvImporter.Row;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.common.csv.CsvImporter.Area;
import com.teamops.common.csv.CsvImporter.ImportSession;
import com.teamops.common.csv.CsvRow;
import com.teamops.common.csv.ImportDtos.CellError;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SliceAuth;

/** Consultant rows are validated before anything is saved: unknown employees and existing consultants are errors. */
class ConsultantCsvImporterTest {

	private final ConsultantRepository consultants = mock(ConsultantRepository.class);

	private final EmployeeRepository employees = mock(EmployeeRepository.class);

	private final RecycleBinService recycleBin = mock(RecycleBinService.class);

	private final ConsultantCsvImporter importer = new ConsultantCsvImporter(consultants, employees, recycleBin);

	private ImportSession<Row> session;

	@BeforeEach
	void setUp() {
		Employee priya = new Employee();
		ReflectionTestUtils.setField(priya, "id", 7L);
		priya.setEmployeeCode("TRI-0042");
		when(employees.findByEmployeeCode("tri-0042")).thenReturn(Optional.of(priya));
		Employee arun = new Employee();
		ReflectionTestUtils.setField(arun, "id", 8L);
		arun.setEmployeeCode("TRI-0043");
		when(employees.findByEmployeeCode("TRI-0043")).thenReturn(Optional.of(arun));
		when(consultants.existsByEmployeeId(8L)).thenReturn(true);
		session = importer.open(SliceAuth.SUPER_ADMIN);
	}

	@Test
	void isABenchImportThatNeedsBenchEdit() {
		assertThat(importer.area()).isEqualTo(Area.BENCH);
		assertThat(importer.permission()).isEqualTo("BENCH_EDIT");
		assertThat(importer.columns()).extracting("name").containsExactlyElementsOf(ConsultantService.CSV_COLUMNS);
	}

	@Test
	void aValidRowUsesTheEmployeeRecordsOwnId() {
		RowReader reader = reader(Map.of("employee_id", "tri-0042", "experience_years", "6.5", "available_from",
				"2026-11-01", "employment_status", "notice period", "skills", "SAP S/4HANA; Tosca; tosca",
				"certifications", "ISTQB Foundation"));

		Row row = session.parse(reader);

		assertThat(reader.errors()).isEmpty();
		assertThat(row.code()).isEqualTo("TRI-0042");
		assertThat(row.employeeId()).isEqualTo(7L);
		assertThat(row.experienceYears()).isEqualByComparingTo("6.5");
		assertThat(row.availableFrom()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(row.employmentStatus()).isEqualTo(EmploymentStatus.NOTICE_PERIOD);
		assertThat(row.skills()).containsExactly("SAP S/4HANA", "Tosca");
		assertThat(row.certifications()).containsExactly("ISTQB Foundation");
		assertThat(session.key(row)).isEqualTo("tri-0042");
	}

	@Test
	void unknownEmployeesAndExistingConsultantsAreErrors() {
		assertThat(errors(Map.of("employee_id", "TRI-9999"))).extracting(CellError::message)
			.containsExactly("No employee record has this ID");
		assertThat(errors(Map.of("employee_id", "TRI-0043"))).extracting(CellError::message)
			.containsExactly("This employee already is a consultant");

		doThrow(ApiException.conflict("IN_RECYCLE_BIN", "This employee belongs to a deleted consultant")).when(recycleBin)
			.requireNotInBin(eq(Consultant.class), eq(Map.of("employee_id", 7L)), any());
		assertThat(errors(Map.of("employee_id", "tri-0042"))).extracting(CellError::message)
			.containsExactly("This employee belongs to a deleted consultant");
	}

	@Test
	void badCellsAreReportedPerColumn() {
		Map<String, String> cells = new HashMap<>();
		cells.put("experience_years", "61");
		cells.put("employment_status", "RETIRED");
		cells.put("available_from", "01/11/2026");
		cells.put("skills", String.join(";", IntStream.rangeClosed(0, BenchText.MAX_SKILLS)
			.mapToObj(i -> "Skill " + i)
			.toList()));
		assertThat(errors(cells)).extracting(CellError::column)
			.containsExactlyInAnyOrder("employee_id", "experience_years", "employment_status", "available_from",
					"skills");
	}

	private List<CellError> errors(Map<String, String> cells) {
		RowReader reader = reader(cells);
		assertThat(session.parse(reader)).isNull();
		return reader.errors();
	}

	private RowReader reader(Map<String, String> cells) {
		return new RowReader(new CsvRow(2, cells), importer.columns());
	}

}
