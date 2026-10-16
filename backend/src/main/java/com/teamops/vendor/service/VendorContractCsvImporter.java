package com.teamops.vendor.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.user.entity.PermissionCodes;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorContract;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.repository.VendorContractRepository;
import com.teamops.vendor.repository.VendorRepository;

/**
 * Imports vendor contracts; the columns match the contract export. Each row names an existing vendor by name. An
 * unknown or blacklisted vendor, or a title the vendor already uses (in the recycle bin too), is an error: nothing is
 * overwritten. Documents are added in the app.
 */
@Component
public class VendorContractCsvImporter implements CsvImporter<VendorContractCsvImporter.Row> {

	public static final String TYPE = "vendor-contracts";

	private static final Pattern CURRENCY = Pattern.compile("[A-Za-z]{3}");

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("vendor", "Name of an existing vendor", "Atlassian"),
			CsvColumn.required("title", "Unique for the vendor", "Jira Cloud Premium"),
			CsvColumn.required("start_date", "YYYY-MM-DD", "2026-03-01"),
			CsvColumn.optional("end_date", "YYYY-MM-DD; empty while open-ended", "2027-02-28"),
			CsvColumn.optional("value", "Contract value, up to two decimals; empty or Hidden for none", "18500.00"),
			CsvColumn.optional("currency", "Three-letter code; INR when empty", "AUD"),
			CsvColumn.optional("renewal_date", "YYYY-MM-DD; reminders use the end date when empty", "2027-03-01"),
			CsvColumn.optional("auto_renew", "yes or no (default)", "yes"),
			CsvColumn.optional("notice_period_days", "Days of notice needed before the renewal, 0 to 730", "30"),
			CsvColumn.optional("notes", "Anything else", "Seats can be reduced at renewal"));

	private final VendorRepository vendors;

	private final VendorContractRepository contracts;

	private final RecycleBinService recycleBin;

	public VendorContractCsvImporter(VendorRepository vendors, VendorContractRepository contracts,
			RecycleBinService recycleBin) {
		this.vendors = vendors;
		this.contracts = contracts;
		this.recycleBin = recycleBin;
	}

	public record Row(Long vendorId, String title, LocalDate startDate, LocalDate endDate, BigDecimal value,
			String currency, LocalDate renewalDate, boolean autoRenew, Integer noticePeriodDays, String notes) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public Area area() {
		return Area.VENDORS;
	}

	@Override
	public String label() {
		return "Vendor contracts";
	}

	@Override
	public String description() {
		return "Contracts of existing vendors. Rows for a title the vendor already uses are rejected.";
	}

	@Override
	public String permission() {
		return PermissionCodes.VENDOR_EDIT;
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
				String vendorName = row.text("vendor", 200);
				String title = row.text("title", 200);
				LocalDate start = row.date("start_date");
				LocalDate end = row.date("end_date");
				// An export by someone without FINANCE_VIEW says "Hidden": that imports as no value.
				BigDecimal value = VendorContractService.HIDDEN.equalsIgnoreCase(row.text("value", 40)) ? null
						: row.amount("value", 2);
				String currency = row.text("currency", 3);
				LocalDate renewal = row.date("renewal_date");
				Boolean autoRenew = yesNo(row, "auto_renew");
				Integer notice = row.integer("notice_period_days", 0, ContractRules.MAX_NOTICE_DAYS);
				String notes = row.text("notes", 5000);
				if (currency != null && !CURRENCY.matcher(currency).matches()) {
					row.error("currency", "Must be a three-letter code like INR or AUD");
				}
				if (value != null && value.precision() - value.scale() > 12) {
					row.error("value", "Must be below 1,000,000,000,000");
				}
				// The same rules as ContractRules.checkDates, reported on the column at fault.
				if (start != null && end != null && end.isBefore(start)) {
					row.error("end_date", "Must be on or after the start date");
				}
				if (start != null && renewal != null && renewal.isBefore(start)) {
					row.error("renewal_date", "Must be on or after the start date");
				}
				if (!row.valid()) {
					return null;
				}
				Vendor vendor = vendors.findByName(vendorName).orElse(null);
				if (vendor == null) {
					row.error("vendor", "No vendor has this name");
					return null;
				}
				if (vendor.getStatus() == VendorStatus.BLACKLISTED) {
					row.error("vendor", "This vendor is blacklisted and takes no new contracts");
					return null;
				}
				if (contracts.existsByVendorIdAndTitle(vendor.getId(), title)) {
					row.error("title", "This vendor already has a contract with this title");
					return null;
				}
				try {
					recycleBin.requireNotInBin(VendorContract.class, Map.of("vendor_id", vendor.getId(), "title", title),
							"This contract");
				}
				catch (ApiException ex) {
					row.error("title", ex.getMessage());
					return null;
				}
				return new Row(vendor.getId(), title, start, end, VendorContractService.money(value),
						VendorText.currency(currency), renewal, Boolean.TRUE.equals(autoRenew), notice, notes);
			}

			@Override
			public String key(Row record) {
				return record.vendorId() + "|" + record.title().toLowerCase(Locale.ROOT);
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					VendorContract contract = new VendorContract();
					contract.setVendor(vendors.getReferenceById(record.vendorId()));
					contract.setTitle(record.title());
					contract.setStartDate(record.startDate());
					contract.setEndDate(record.endDate());
					contract.setValue(record.value());
					contract.setCurrency(record.currency());
					contract.setRenewalDate(record.renewalDate());
					contract.setAutoRenew(record.autoRenew());
					contract.setNoticePeriodDays(record.noticePeriodDays());
					contract.setNotes(record.notes());
					contracts.save(contract);
				}
				return records.size();
			}

		};
	}

	/** yes/no, true/false, y/n or 1/0, ignoring case; null when empty. */
	static Boolean yesNo(RowReader row, String column) {
		String value = row.text(column, 5);
		if (value == null) {
			return null;
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "yes", "y", "true", "1" -> true;
			case "no", "n", "false", "0" -> false;
			default -> {
				row.error(column, "Must be yes or no");
				yield null;
			}
		};
	}

}
