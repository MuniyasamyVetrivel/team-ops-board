package com.teamops.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.teamops.common.settings.AppSettingsService;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.finance.entity.BillingCycle;
import com.teamops.finance.entity.LicenceStatus;
import com.teamops.finance.repository.ExchangeRateRepository;
import com.teamops.finance.repository.FinanceLicenceRepository;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.repository.VendorRepository;

/** Licence and rate rows are validated before anything is saved; card numbers never get in. */
class FinanceCsvImportersTest {

	private final FinanceLicenceRepository licences = mock(FinanceLicenceRepository.class);

	private final VendorRepository vendors = mock(VendorRepository.class);

	private final UserRepository users = mock(UserRepository.class);

	private final DepartmentRepository departments = mock(DepartmentRepository.class);

	private final ExchangeRateRepository rates = mock(ExchangeRateRepository.class);

	private final RecycleBinService recycleBin = mock(RecycleBinService.class);

	private final AppSettingsService settings = mock(AppSettingsService.class);

	private final LicenceCsvImporter licenceImporter = new LicenceCsvImporter(licences, vendors, users, departments,
			recycleBin, settings);

	private final ExchangeRateCsvImporter rateImporter = new ExchangeRateCsvImporter(rates, recycleBin);

	private ImportSession<LicenceCsvImporter.Row> licenceSession;

	private ImportSession<ExchangeRateCsvImporter.Row> rateSession;

	@BeforeEach
	void setUp() {
		Vendor atlassian = new Vendor();
		ReflectionTestUtils.setField(atlassian, "id", 5L);
		when(vendors.findByName("atlassian")).thenReturn(Optional.of(atlassian));
		Vendor shady = new Vendor();
		shady.setStatus(VendorStatus.BLACKLISTED);
		when(vendors.findByName("Shady Supplies")).thenReturn(Optional.of(shady));
		User priya = TestFixtures.user(11L, "priya@example.com", TestFixtures.role(3L, "EMPLOYEE"));
		when(users.findByEmailIgnoreCase("priya@example.com")).thenReturn(Optional.of(priya));
		User gone = TestFixtures.user(12L, "gone@example.com", TestFixtures.role(3L, "EMPLOYEE"));
		gone.setStatus(UserStatus.DISABLED);
		when(users.findByEmailIgnoreCase("gone@example.com")).thenReturn(Optional.of(gone));
		Department it = new Department();
		ReflectionTestUtils.setField(it, "id", 2L);
		when(departments.findByCode("IT")).thenReturn(Optional.of(it));
		when(licences.existsByName("Jira Cloud")).thenReturn(true);
		when(rates.existsByCurrencyAndEffectiveFrom("INR", LocalDate.of(2026, 10, 1))).thenReturn(true);
		when(settings.financeDefaultCurrency()).thenReturn("AUD");
		licenceSession = licenceImporter.open(SliceAuth.SUPER_ADMIN);
		rateSession = rateImporter.open(SliceAuth.SUPER_ADMIN);
	}

	@Test
	void bothAreFinanceImportsThatNeedFinanceEditAndMatchTheExports() {
		assertThat(licenceImporter.area()).isEqualTo(Area.FINANCE);
		assertThat(rateImporter.area()).isEqualTo(Area.FINANCE);
		assertThat(licenceImporter.permission()).isEqualTo("FINANCE_EDIT");
		assertThat(licenceImporter.columns()).extracting(CsvColumn::name).containsExactlyElementsOf(LicenceService.CSV_COLUMNS);
		assertThat(rateImporter.columns()).extracting(CsvColumn::name).containsExactlyElementsOf(ExchangeRateService.CSV_COLUMNS);
	}

	@Test
	void aValidLicenceRowLinksItsVendorOwnerDepartmentAndSeats() {
		RowReader reader = reader(licenceImporter.columns(), Map.ofEntries(Map.entry("name", "Confluence"),
				Map.entry("vendor", "atlassian"), Map.entry("owner_email", "Priya@Example.com"), Map.entry("department", "it"),
				Map.entry("seats_paid", "20"), Map.entry("billing_cycle", "yearly"), Map.entry("cost", "9,600"),
				Map.entry("currency", "aud"), Map.entry("payment_method", "Corporate Amex"), Map.entry("card_last4", "1234"),
				Map.entry("assigned_users", "priya@example.com; PRIYA@example.com;")));

		LicenceCsvImporter.Row row = licenceSession.parse(reader);

		assertThat(reader.errors()).isEmpty();
		assertThat(row.vendorId()).isEqualTo(5L);
		assertThat(row.ownerId()).isEqualTo(11L);
		assertThat(row.departmentId()).isEqualTo(2L);
		assertThat(row.billingCycle()).isEqualTo(BillingCycle.YEARLY);
		assertThat(row.cost()).isEqualByComparingTo("9600.00");
		assertThat(row.currency()).isEqualTo("AUD");
		assertThat(row.status()).isEqualTo(LicenceStatus.ACTIVE);
		assertThat(row.assignedUserIds()).containsExactly(11L);
		assertThat(licenceSession.key(row)).isEqualTo("confluence");
	}

	@Test
	void licenceRowsWithProblemsSayWhichCellIsWrong() {
		Map<String, String> valid = Map.of("name", "Confluence", "billing_cycle", "MONTHLY");
		assertThat(licenceErrors(with(valid, "payment_method", "Visa 4111 1111 1111 1111")))
			.containsExactly("payment_method: Never a card number: use a label and at most the last 4 digits");
		assertThat(licenceErrors(with(valid, "card_last4", "12a4"))).containsExactly("card_last4: Exactly 4 digits, or empty");
		assertThat(licenceErrors(with(valid, "name", "Jira Cloud")))
			.containsExactly("name: A tool or licence with this name already exists");
		assertThat(licenceErrors(with(valid, "vendor", "Nobody Ltd"))).containsExactly("vendor: No vendor has this name");
		assertThat(licenceErrors(with(valid, "vendor", "Shady Supplies"))).containsExactly("vendor: This vendor is blacklisted");
		assertThat(licenceErrors(with(valid, "assigned_users", "gone@example.com")))
			.singleElement()
			.satisfies(e -> assertThat(e).startsWith("assigned_users: ").endsWith("is not an active user"));
		assertThat(licenceErrors(with(valid, "department", "FINANCE"))).containsExactly("department: No department has this code");
		assertThat(licenceErrors(Map.of("name", "Confluence"))).containsExactly("billing_cycle: Required");
		Map<String, String> dates = new HashMap<>(with(valid, "start_date", "2026-03-01"));
		dates.put("renewal_date", "2026-02-01");
		assertThat(licenceErrors(dates)).containsExactly("renewal_date: Must be on or after the start date");
	}

	@Test
	void rateRowsAreRatesToAudAndNeverForAudItself() {
		RowReader valid = reader(rateImporter.columns(), Map.of("currency", "usd", "effective_from", "2026-10-01",
				"rate_to_aud", "1.52345678"));
		ExchangeRateCsvImporter.Row row = rateSession.parse(valid);
		assertThat(valid.errors()).isEmpty();
		assertThat(row.currency()).isEqualTo("USD");
		assertThat(rateSession.key(row)).isEqualTo("USD|2026-10-01");

		assertThat(rateErrors(Map.of("currency", "AUD", "effective_from", "2026-10-01", "rate_to_aud", "1")))
			.containsExactly("currency: AUD is the base currency: its rate is always 1");
		assertThat(rateErrors(Map.of("currency", "USD", "effective_from", "2026-10-01", "rate_to_aud", "0")))
			.containsExactly("rate_to_aud: Must be above 0 and at most 1,000,000");
		assertThat(rateErrors(Map.of("currency", "INR", "effective_from", "2026-10-01", "rate_to_aud", "0.0182")))
			.containsExactly("effective_from: INR already has a rate from this day");
	}

	private List<String> licenceErrors(Map<String, String> cells) {
		RowReader reader = reader(licenceImporter.columns(), cells);
		licenceSession.parse(reader);
		return messages(reader);
	}

	private List<String> rateErrors(Map<String, String> cells) {
		RowReader reader = reader(rateImporter.columns(), cells);
		rateSession.parse(reader);
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
