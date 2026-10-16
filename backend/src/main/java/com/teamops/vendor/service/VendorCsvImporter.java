package com.teamops.vendor.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.user.entity.PermissionCodes;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorCategory;
import com.teamops.vendor.entity.VendorContact;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.repository.VendorRepository;

/**
 * Imports vendors with their main contact; the columns match the vendor export. A name already used by a vendor (in
 * the recycle bin too) is an error: nothing is overwritten.
 */
@Component
public class VendorCsvImporter implements CsvImporter<VendorCsvImporter.Row> {

	public static final String TYPE = "vendors";

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("name", "Vendor name, unique", "Atlassian"),
			CsvColumn.optional("tax_id", "ABN, GST or another tax ID", "53 102 443 916"),
			CsvColumn.optional("category",
					"SOFTWARE, HARDWARE, RECRUITMENT_AGENCY, MARKETING, SERVICES or OTHER (default)", "SOFTWARE"),
			CsvColumn.optional("country", "Where the vendor is based", "Australia"),
			CsvColumn.optional("address", "Postal address", "341 George St, Sydney NSW 2000"),
			CsvColumn.optional("website", "Starting with http:// or https://", "https://www.atlassian.com"),
			CsvColumn.optional("payment_terms", "As agreed with the vendor", "Net 30"),
			CsvColumn.optional("status", "ACTIVE (default), INACTIVE or BLACKLISTED", "ACTIVE"),
			CsvColumn.optional("rating", "1 (poor) to 5 (excellent)", "4"),
			CsvColumn.optional("notes", "Anything else", "Annual true-up in March"),
			CsvColumn.optional("contact_name", "Main contact; needed when any other contact column is filled",
					"Priya Shah"),
			CsvColumn.optional("contact_role", "Main contact's role", "Account Executive"),
			CsvColumn.optional("contact_email", "Main contact's email", "priya.shah@example.com"),
			CsvColumn.optional("contact_phone", "Main contact's phone", "+61 2 9000 0000"));

	private final VendorRepository vendors;

	private final RecycleBinService recycleBin;

	public VendorCsvImporter(VendorRepository vendors, RecycleBinService recycleBin) {
		this.vendors = vendors;
		this.recycleBin = recycleBin;
	}

	public record Contact(String name, String role, String email, String phone) {
	}

	public record Row(String name, String taxId, VendorCategory category, String country, String address,
			String website, String paymentTerms, VendorStatus status, Integer rating, String notes, Contact contact) {
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
		return "Vendors";
	}

	@Override
	public String description() {
		return "Vendors with their main contact. Rows for a vendor name that already exists are rejected.";
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
				String name = row.text("name", 200);
				String taxId = row.text("tax_id", 50);
				VendorCategory category = row.choice("category", VendorCategory.class);
				String country = row.text("country", 100);
				String address = row.text("address", 500);
				String website = row.url("website", 300, false);
				String paymentTerms = row.text("payment_terms", 100);
				VendorStatus status = row.choice("status", VendorStatus.class);
				Integer rating = row.integer("rating", 1, 5);
				String notes = row.text("notes", 5000);
				String contactName = row.text("contact_name", 150);
				String contactRole = row.text("contact_role", 100);
				String contactEmail = row.email("contact_email");
				String contactPhone = row.text("contact_phone", 50);
				boolean anyContact = contactRole != null || contactEmail != null || contactPhone != null;
				if (contactName == null && anyContact) {
					row.error("contact_name", "Name the main contact, or leave every contact column empty");
				}
				if (!row.valid()) {
					return null;
				}
				if (vendors.existsByName(name)) {
					row.error("name", "A vendor with this name already exists");
					return null;
				}
				try {
					recycleBin.requireNotInBin(Vendor.class, Map.of("name", name), "This vendor name");
				}
				catch (ApiException ex) {
					row.error("name", ex.getMessage());
					return null;
				}
				Contact contact = contactName == null ? null
						: new Contact(contactName, contactRole, contactEmail, contactPhone);
				return new Row(name, taxId, category == null ? VendorCategory.OTHER : category, country, address,
						website, paymentTerms, status == null ? VendorStatus.ACTIVE : status, rating, notes, contact);
			}

			@Override
			public String key(Row record) {
				return record.name().toLowerCase(Locale.ROOT);
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					Vendor vendor = new Vendor();
					vendor.setName(record.name());
					vendor.setTaxId(record.taxId());
					vendor.setCategory(record.category());
					vendor.setCountry(record.country());
					vendor.setAddress(record.address());
					vendor.setWebsite(record.website());
					vendor.setPaymentTerms(record.paymentTerms());
					vendor.setStatus(record.status());
					vendor.setRating(record.rating());
					vendor.setNotes(record.notes());
					if (record.contact() != null) {
						VendorContact contact = new VendorContact();
						contact.setVendor(vendor);
						contact.setName(record.contact().name());
						contact.setRole(record.contact().role());
						contact.setEmail(record.contact().email());
						contact.setPhone(record.contact().phone());
						vendor.getContacts().add(contact);
					}
					vendors.save(vendor);
				}
				return records.size();
			}

		};
	}

}
