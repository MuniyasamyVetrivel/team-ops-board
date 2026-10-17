package com.teamops.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.finance.dto.FinanceDtos;
import com.teamops.finance.entity.BillingCycle;
import com.teamops.finance.entity.FinanceLicence;
import com.teamops.finance.entity.LicenceAssignment;
import com.teamops.finance.entity.LicenceStatus;
import com.teamops.finance.repository.FinanceLicenceRepository;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.user.entity.PermissionCodes;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.repository.VendorRepository;

/**
 * Imports tools and licences; the columns match the licence export. Vendors are named, owners and assigned users given
 * by email (active users only), departments by code. A name already used (in the recycle bin too), an unknown vendor,
 * user or department, or a payment method holding a card number is an error: nothing is overwritten.
 */
@Component
public class LicenceCsvImporter implements CsvImporter<LicenceCsvImporter.Row> {

	public static final String TYPE = "licences";

	private static final Pattern CURRENCY = Pattern.compile("[A-Za-z]{3}");

	private static final Pattern LAST4 = Pattern.compile("\\d{4}");

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("name", "Tool or licence name, unique", "Jira Cloud Premium"),
			CsvColumn.optional("vendor", "Name of an existing vendor", "Atlassian"),
			CsvColumn.optional("owner_email", "Email of an active user who looks after it", "rakesh@example.com"),
			CsvColumn.optional("department", "Department code", "IT"),
			CsvColumn.optional("seats_paid", "Seats paid for, 0 to 100,000", "20"),
			CsvColumn.required("billing_cycle", "MONTHLY, YEARLY or ONE_OFF", "YEARLY"),
			CsvColumn.optional("cost", "Cost per billing cycle, up to two decimals", "9600.00"),
			CsvColumn.optional("currency", "Three-letter code; the Finance default currency when empty", "AUD"),
			CsvColumn.optional("start_date", "YYYY-MM-DD", "2026-03-01"),
			CsvColumn.optional("renewal_date", "YYYY-MM-DD; the end of a trial for trials", "2027-03-01"),
			CsvColumn.optional("payment_method", "A label, never a card number", "Corporate Amex"),
			CsvColumn.optional("card_last4", "At most the last 4 digits of the card", "1234"),
			CsvColumn.optional("status", "ACTIVE (default), TRIAL or CANCELLED", "ACTIVE"),
			CsvColumn.optional("assigned_users", "Emails of the users using a seat, separated by semicolons",
					"priya@example.com; arun@example.com"),
			CsvColumn.optional("notes", "Anything else", "True-up in March"));

	private final FinanceLicenceRepository licences;

	private final VendorRepository vendors;

	private final UserRepository users;

	private final DepartmentRepository departments;

	private final RecycleBinService recycleBin;

	private final AppSettingsService settings;

	public LicenceCsvImporter(FinanceLicenceRepository licences, VendorRepository vendors, UserRepository users,
			DepartmentRepository departments, RecycleBinService recycleBin, AppSettingsService settings) {
		this.settings = settings;
		this.licences = licences;
		this.vendors = vendors;
		this.users = users;
		this.departments = departments;
		this.recycleBin = recycleBin;
	}

	public record Row(String name, Long vendorId, Long ownerId, Long departmentId, Integer seatsPaid,
			BillingCycle billingCycle, BigDecimal cost, String currency, LocalDate startDate, LocalDate renewalDate,
			String paymentMethod, String cardLast4, LicenceStatus status, List<Long> assignedUserIds, String notes) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public Area area() {
		return Area.FINANCE;
	}

	@Override
	public String label() {
		return "Tools and licences";
	}

	@Override
	public String description() {
		return "Tools and licences with their seats, cost and renewal. Rows for a name that already exists are rejected.";
	}

	@Override
	public String permission() {
		return PermissionCodes.FINANCE_EDIT;
	}

	@Override
	public List<CsvColumn> columns() {
		return COLUMNS;
	}

	@Override
	public ImportSession<Row> open(AuthenticatedUser actor) {
		String defaultCurrency = settings.financeDefaultCurrency();
		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String name = row.text("name", 200);
				String vendorName = row.text("vendor", 200);
				String ownerEmail = row.email("owner_email");
				String departmentCode = row.text("department", 50);
				Integer seats = row.integer("seats_paid", 0, LicenceRules.MAX_SEATS);
				BillingCycle cycle = row.choice("billing_cycle", BillingCycle.class);
				BigDecimal cost = row.amount("cost", 2);
				String currency = row.text("currency", 3);
				LocalDate start = row.date("start_date");
				LocalDate renewal = row.date("renewal_date");
				String paymentMethod = row.text("payment_method", 60);
				String last4 = row.text("card_last4", 4);
				LicenceStatus status = row.choice("status", LicenceStatus.class);
				List<String> emails = emails(row);
				String notes = row.text("notes", 5000);
				if (currency != null && !CURRENCY.matcher(currency).matches()) {
					row.error("currency", "Must be a three-letter code like INR or AUD");
				}
				if (cost != null && cost.precision() - cost.scale() > 12) {
					row.error("cost", "Must be below 1,000,000,000,000");
				}
				if (start != null && renewal != null && renewal.isBefore(start)) {
					row.error("renewal_date", "Must be on or after the start date");
				}
				if (paymentMethod != null && LicenceRules.hasLongDigitRun(paymentMethod)) {
					row.error("payment_method", "Never a card number: use a label and at most the last 4 digits");
				}
				if (last4 != null && !LAST4.matcher(last4).matches()) {
					row.error("card_last4", "Exactly 4 digits, or empty");
				}
				if (!row.valid()) {
					return null;
				}
				if (licences.existsByName(name)) {
					row.error("name", "A tool or licence with this name already exists");
					return null;
				}
				try {
					recycleBin.requireNotInBin(FinanceLicence.class, Map.of("name", name), "This licence name");
				}
				catch (ApiException ex) {
					row.error("name", ex.getMessage());
					return null;
				}
				Long vendorId = null;
				if (vendorName != null) {
					Vendor vendor = vendors.findByName(vendorName).orElse(null);
					if (vendor == null) {
						row.error("vendor", "No vendor has this name");
						return null;
					}
					if (vendor.getStatus() == VendorStatus.BLACKLISTED) {
						row.error("vendor", "This vendor is blacklisted");
						return null;
					}
					vendorId = vendor.getId();
				}
				Long ownerId = null;
				if (ownerEmail != null) {
					User owner = activeUser(row, "owner_email", ownerEmail);
					if (owner == null) {
						return null;
					}
					ownerId = owner.getId();
				}
				Long departmentId = null;
				if (departmentCode != null) {
					Department department = departments.findByCode(departmentCode.toUpperCase(Locale.ROOT)).orElse(null);
					if (department == null) {
						row.error("department", "No department has this code");
						return null;
					}
					departmentId = department.getId();
				}
				Set<Long> assigned = new LinkedHashSet<>();
				for (String email : emails) {
					User user = activeUser(row, "assigned_users", email);
					if (user == null) {
						return null;
					}
					assigned.add(user.getId());
				}
				return new Row(name, vendorId, ownerId, departmentId, seats, cycle,
						cost == null ? null : cost.setScale(2, RoundingMode.UNNECESSARY),
						FinanceText.currency(currency, defaultCurrency), start, renewal, paymentMethod, last4,
						status == null ? LicenceStatus.ACTIVE : status, List.copyOf(assigned), notes);
			}

			@Override
			public String key(Row record) {
				return record.name().toLowerCase(Locale.ROOT);
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					FinanceLicence licence = new FinanceLicence();
					licence.setName(record.name());
					licence.setVendorId(record.vendorId());
					licence.setOwner(record.ownerId() == null ? null : users.getReferenceById(record.ownerId()));
					licence.setDepartment(
							record.departmentId() == null ? null : departments.getReferenceById(record.departmentId()));
					licence.setSeatsPaid(record.seatsPaid());
					licence.setBillingCycle(record.billingCycle());
					licence.setCost(record.cost());
					licence.setCurrency(record.currency());
					licence.setStartDate(record.startDate());
					licence.setRenewalDate(record.renewalDate());
					licence.setPaymentMethod(record.paymentMethod());
					licence.setCardLast4(record.cardLast4());
					licence.setStatus(record.status());
					licence.setNotes(record.notes());
					for (Long userId : record.assignedUserIds()) {
						LicenceAssignment assignment = new LicenceAssignment();
						assignment.setLicence(licence);
						assignment.setUser(users.getReferenceById(userId));
						licence.getAssignments().add(assignment);
					}
					licences.save(licence);
				}
				return records.size();
			}

			private User activeUser(RowReader row, String column, String email) {
				User user = users.findByEmailIgnoreCase(email).orElse(null);
				if (user == null) {
					row.error(column, "No user has the email " + email);
					return null;
				}
				if (!user.isActive()) {
					row.error(column, user.getFullName() + " is not an active user");
					return null;
				}
				return user;
			}

		};
	}

	/** The assigned users' emails: semicolon-separated, blanks dropped, each a valid address. */
	private static List<String> emails(RowReader row) {
		String cell = row.text("assigned_users", FinanceDtos.MAX_ASSIGNED_USERS * 260);
		List<String> emails = new ArrayList<>();
		if (cell == null) {
			return emails;
		}
		for (String part : cell.split(LicenceService.LIST_SEPARATOR)) {
			String email = part.trim().toLowerCase(Locale.ROOT);
			if (email.isEmpty()) {
				continue;
			}
			if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
				row.error("assigned_users", "Not a valid email address: " + email);
				continue;
			}
			if (!emails.contains(email)) {
				emails.add(email);
			}
		}
		if (emails.size() > FinanceDtos.MAX_ASSIGNED_USERS) {
			row.error("assigned_users", "At most " + FinanceDtos.MAX_ASSIGNED_USERS + " users");
		}
		return emails;
	}

}
