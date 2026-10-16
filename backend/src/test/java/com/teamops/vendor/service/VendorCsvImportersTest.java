package com.teamops.vendor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter.Area;
import com.teamops.common.csv.CsvImporter.ImportSession;
import com.teamops.common.csv.CsvRow;
import com.teamops.common.csv.ImportDtos.CellError;
import com.teamops.common.csv.RowReader;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SliceAuth;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorCategory;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.repository.VendorContractRepository;
import com.teamops.vendor.repository.VendorRepository;

/** Vendor and contract rows are validated before anything is saved; nothing that exists is overwritten. */
class VendorCsvImportersTest {

	private final VendorRepository vendors = mock(VendorRepository.class);

	private final VendorContractRepository contracts = mock(VendorContractRepository.class);

	private final RecycleBinService recycleBin = mock(RecycleBinService.class);

	private final VendorCsvImporter vendorImporter = new VendorCsvImporter(vendors, recycleBin);

	private final VendorContractCsvImporter contractImporter = new VendorContractCsvImporter(vendors, contracts,
			recycleBin);

	private ImportSession<VendorCsvImporter.Row> vendorSession;

	private ImportSession<VendorContractCsvImporter.Row> contractSession;

	@BeforeEach
	void setUp() {
		when(vendors.existsByName("Atlassian")).thenReturn(true);
		Vendor atlassian = new Vendor();
		ReflectionTestUtils.setField(atlassian, "id", 5L);
		atlassian.setName("Atlassian");
		when(vendors.findByName("atlassian")).thenReturn(Optional.of(atlassian));
		Vendor blacklisted = new Vendor();
		ReflectionTestUtils.setField(blacklisted, "id", 6L);
		blacklisted.setStatus(VendorStatus.BLACKLISTED);
		when(vendors.findByName("Shady Supplies")).thenReturn(Optional.of(blacklisted));
		when(contracts.existsByVendorIdAndTitle(5L, "Jira Cloud")).thenReturn(true);
		vendorSession = vendorImporter.open(SliceAuth.SUPER_ADMIN);
		contractSession = contractImporter.open(SliceAuth.SUPER_ADMIN);
	}

	@Test
	void bothAreVendorImportsThatNeedVendorEditAndMatchTheExports() {
		assertThat(vendorImporter.area()).isEqualTo(Area.VENDORS);
		assertThat(contractImporter.area()).isEqualTo(Area.VENDORS);
		assertThat(vendorImporter.permission()).isEqualTo("VENDOR_EDIT");
		assertThat(contractImporter.permission()).isEqualTo("VENDOR_EDIT");
		assertThat(vendorImporter.columns()).extracting(CsvColumn::name).containsExactlyElementsOf(VendorService.CSV_COLUMNS);
		assertThat(contractImporter.columns()).extracting(CsvColumn::name)
			.containsExactlyElementsOf(VendorContractService.CSV_COLUMNS);
	}

	@Test
	void aValidVendorRowKeepsItsMainContact() {
		RowReader reader = reader(vendorImporter.columns(), Map.of("name", "Canva", "category", "software", "status",
				"inactive", "rating", "4", "website", "https://www.canva.com", "contact_name", "Meera", "contact_email",
				"Meera@Canva.com"));

		VendorCsvImporter.Row row = vendorSession.parse(reader);

		assertThat(reader.errors()).isEmpty();
		assertThat(row.category()).isEqualTo(VendorCategory.SOFTWARE);
		assertThat(row.status()).isEqualTo(VendorStatus.INACTIVE);
		assertThat(row.rating()).isEqualTo(4);
		assertThat(row.contact().email()).isEqualTo("meera@canva.com");
		assertThat(vendorSession.key(row)).isEqualTo("canva");
	}

	@Test
	void vendorRowsWithProblemsSayWhichCellIsWrong() {
		assertThat(vendorErrors(Map.of("name", "Atlassian"))).containsExactly("name: A vendor with this name already exists");
		assertThat(vendorErrors(Map.of("name", "Canva", "rating", "6"))).containsExactly("rating: Must be between 1 and 5");
		assertThat(vendorErrors(Map.of("name", "Canva", "website", "canva.com"))).hasSize(1).allMatch(e -> e.startsWith("website"));
		assertThat(vendorErrors(Map.of("name", "Canva", "contact_email", "meera@canva.com")))
			.containsExactly("contact_name: Name the main contact, or leave every contact column empty");
		assertThat(vendorErrors(Map.of("category", "FOOD"))).hasSize(2);
	}

	@Test
	void aValidContractRowNamesItsVendorAndDefaultsTheCurrency() {
		RowReader reader = reader(contractImporter.columns(), Map.of("vendor", "atlassian", "title", "Confluence",
				"start_date", "2026-03-01", "end_date", "2027-02-28", "value", "18,500.5", "auto_renew", "Yes",
				"notice_period_days", "30"));

		VendorContractCsvImporter.Row row = contractSession.parse(reader);

		assertThat(reader.errors()).isEmpty();
		assertThat(row.vendorId()).isEqualTo(5L);
		assertThat(row.value()).isEqualByComparingTo(new BigDecimal("18500.50"));
		assertThat(row.value().scale()).isEqualTo(2);
		assertThat(row.currency()).isEqualTo("INR");
		assertThat(row.autoRenew()).isTrue();
		assertThat(row.endDate()).isEqualTo(LocalDate.of(2027, 2, 28));
		assertThat(contractSession.key(row)).isEqualTo("5|confluence");
	}

	@Test
	void contractRowsWithProblemsSayWhichCellIsWrong() {
		Map<String, String> valid = Map.of("vendor", "atlassian", "title", "Confluence", "start_date", "2026-03-01");
		assertThat(contractErrors(with(valid, "vendor", "Nobody Ltd"))).containsExactly("vendor: No vendor has this name");
		assertThat(contractErrors(with(valid, "vendor", "Shady Supplies")))
			.containsExactly("vendor: This vendor is blacklisted and takes no new contracts");
		assertThat(contractErrors(with(valid, "title", "Jira Cloud")))
			.containsExactly("title: This vendor already has a contract with this title");
		assertThat(contractErrors(with(valid, "end_date", "2026-02-28"))).containsExactly("end_date: Must be on or after the start date");
		assertThat(contractErrors(with(valid, "renewal_date", "2026-02-01")))
			.containsExactly("renewal_date: Must be on or after the start date");
		assertThat(contractErrors(with(valid, "currency", "Rupees"))).hasSize(1);
		assertThat(contractErrors(with(valid, "auto_renew", "maybe"))).containsExactly("auto_renew: Must be yes or no");
		assertThat(contractErrors(with(valid, "notice_period_days", "800"))).containsExactly("notice_period_days: Must be between 0 and 730");
		assertThat(contractErrors(with(valid, "value", "-5"))).containsExactly("value: Cannot be negative");
	}

	@Test
	void aHiddenValueFromAnExportWithoutFinanceViewImportsAsNoValue() {
		RowReader reader = reader(contractImporter.columns(), Map.of("vendor", "atlassian", "title", "Confluence",
				"start_date", "2026-03-01", "value", "Hidden"));

		VendorContractCsvImporter.Row row = contractSession.parse(reader);

		assertThat(reader.errors()).isEmpty();
		assertThat(row.value()).isNull();
	}

	private List<String> vendorErrors(Map<String, String> cells) {
		RowReader reader = reader(vendorImporter.columns(), cells);
		vendorSession.parse(reader);
		return messages(reader);
	}

	private List<String> contractErrors(Map<String, String> cells) {
		RowReader reader = reader(contractImporter.columns(), cells);
		contractSession.parse(reader);
		return messages(reader);
	}

	private static List<String> messages(RowReader reader) {
		return reader.errors().stream().map((CellError e) -> e.column() + ": " + e.message()).toList();
	}

	private static Map<String, String> with(Map<String, String> cells, String column, String value) {
		Map<String, String> copy = new HashMap<>(cells);
		copy.put(column, value);
		return copy;
	}

	private static RowReader reader(List<CsvColumn> columns, Map<String, String> cells) {
		return new RowReader(new CsvRow(2, cells), columns);
	}

}
