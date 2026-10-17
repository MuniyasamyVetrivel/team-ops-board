package com.teamops.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.finance.entity.BillingCycle;
import com.teamops.finance.entity.ExpenseCategory;
import com.teamops.finance.entity.LicenceStatus;
import com.teamops.user.dto.UserSummary;
import com.teamops.vendor.dto.VendorDtos.VendorRef;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API records for the Finance Tracker: tools and licences, exchange rates. */
public final class FinanceDtos {

	public static final int MAX_ASSIGNED_USERS = 1000;

	private FinanceDtos() {
	}

	// --- exchange rates ---------------------------------------------------------------------------------------

	/**
	 * @param unitsPerAud how many of the currency one AUD buys (1 ÷ rate); computed
	 * @param inForce this is the rate in force today for its currency; computed on the business date
	 */
	public record ExchangeRateDto(Long id, String currency, LocalDate effectiveFrom, BigDecimal rateToAud,
			BigDecimal unitsPerAud, String source, boolean inForce, Integer version, Instant createdAt,
			Instant updatedAt) {

	}

	/** One unit of {@code currency} is {@code rateToAud} AUD from {@code effectiveFrom}. */
	public record SaveExchangeRate(
			@NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter currency code") String currency,
			@NotNull LocalDate effectiveFrom,
			@NotNull @DecimalMin(value = "0.00000001") @DecimalMax("1000000") @Digits(integer = 10, fraction = 8) BigDecimal rateToAud,
			@Size(max = 150) String source, Integer version) {

	}

	/**
	 * An amount in AUD at the rate in force on {@code date} ({@code rate} 1 for AUD itself, with no rate date).
	 * @param rateFrom the date the rate used took effect
	 */
	public record Conversion(BigDecimal amount, String currency, LocalDate date, BigDecimal rate, LocalDate rateFrom,
			BigDecimal aud) {

	}

	// --- tools and licences -----------------------------------------------------------------------------------

	/** The AUD rate a licence's cost was converted at, and the date it took effect (null for AUD itself). */
	public record AudRate(BigDecimal rate, LocalDate effectiveFrom) {

	}

	/**
	 * Computed on today's business date, never stored:
	 * @param vendorId the vendor even while it is in the recycle bin ({@code vendor} is then null)
	 * @param seatsUsed the assigned users
	 * @param seatsFree paid seats not assigned yet; null without paid seats
	 * @param utilisationPercent seats used ÷ seats paid × 100; null without paid seats
	 * @param underUsed below 70% of the paid seats, while not cancelled
	 * @param annualCost the cost per year in {@code currency} (12 × monthly, or yearly); null for one-off
	 * @param annualCostAud {@code annualCost} in AUD at today's rate; null without a rate for the currency
	 * @param audRate the rate used; null without one
	 * @param daysUntilRenewal while the renewal is today or later and the licence is not cancelled
	 */
	public record LicenceDto(Long id, String name, Long vendorId, VendorRef vendor, UserSummary owner,
			DepartmentSummary department, Integer seatsPaid, int seatsUsed, Integer seatsFree,
			BigDecimal utilisationPercent, boolean underUsed, boolean overAssigned, List<UserSummary> assignedUsers,
			BillingCycle billingCycle, BigDecimal cost, String currency, BigDecimal annualCost, BigDecimal annualCostAud,
			AudRate audRate, LocalDate startDate, LocalDate renewalDate, Long daysUntilRenewal, String paymentMethod,
			String cardLast4, LicenceStatus status, String notes, Integer version, Instant createdAt, Instant updatedAt) {

	}

	/**
	 * {@code assignedUserIds} replaces the licence's assigned users (each a seat used). {@code currency} defaults to
	 * INR and {@code status} to ACTIVE; {@code version} is required for an update.
	 */
	public record SaveLicence(@NotBlank @Size(max = 200) String name, Long vendorId, Long ownerId, Long departmentId,
			@Min(0) @Max(100_000) Integer seatsPaid, @NotNull BillingCycle billingCycle,
			@DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal cost,
			@Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter currency code") String currency,
			LocalDate startDate, LocalDate renewalDate, @Size(max = 60) String paymentMethod,
			@Pattern(regexp = "\\d{4}", message = "must be exactly the last 4 digits") String cardLast4,
			LicenceStatus status, @Size(max = MAX_ASSIGNED_USERS) List<@NotNull Long> assignedUserIds,
			@Size(max = 5000) String notes, Integer version) {

	}


	// --- expenses ---------------------------------------------------------------------------------------------

	/** The Approval an expense is linked to (code, title and status; never its amount). */
	public record ApprovalRef(Long id, String code, String title, String status) {

	}

	public record ReceiptDto(Long fileId, String fileName, String contentType, long sizeBytes, Instant addedAt) {

	}

	/**
	 * {@code amount} is the total in {@code currency}, GST included. Stored at the expense date (brief: "an AUD
	 * equivalent at a stored exchange rate"): {@code audRate} (1 for AUD), {@code audRateDate} (null for AUD) and
	 * {@code amountAud}. Computed: {@code amountExGst} and {@code gstAud}.
	 * @param vendorId the vendor even while it is in the recycle bin ({@code vendor} is then null)
	 */
	public record ExpenseDto(Long id, LocalDate expenseDate, ExpenseCategory category, Long vendorId, VendorRef vendor,
			String description, BigDecimal amount, BigDecimal gst, BigDecimal amountExGst, String currency,
			BigDecimal audRate, LocalDate audRateDate, BigDecimal amountAud, BigDecimal gstAud,
			DepartmentSummary department, ReceiptDto receipt, ApprovalRef approval, String notes, Integer version,
			Instant createdAt, Instant updatedAt) {

	}

	/**
	 * {@code currency} defaults to the Finance default currency. The AUD rate is taken from the exchange rates on the
	 * expense date when the expense is created, and again when its date or currency changes (400 NO_EXCHANGE_RATE when
	 * there is none). {@code version} is required for an update.
	 */
	public record SaveExpense(@NotNull LocalDate expenseDate, @NotNull ExpenseCategory category, Long vendorId,
			@NotBlank @Size(max = 500) String description,
			@NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal amount,
			@DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal gst,
			@Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter currency code") String currency,
			Long departmentId, Long approvalId, @Size(max = 5000) String notes, Integer version) {

	}

	// --- budgets ----------------------------------------------------------------------------------------------

	/**
	 * A department's budget for a month in AUD, with the month's actual AUD spend; computed: {@code remaining},
	 * {@code usedPercent} (null for a zero budget), {@code overBudget} and {@code overBy}.
	 */
	public record BudgetDto(Long id, DepartmentSummary department, int year, int month, BigDecimal amount,
			BigDecimal actual, BigDecimal remaining, BigDecimal usedPercent, boolean overBudget, BigDecimal overBy,
			String notes, Integer version, Instant createdAt, Instant updatedAt) {

	}

	public record SaveBudget(@NotNull Long departmentId, @Min(2000) @Max(2100) int year, @Min(1) @Max(12) int month,
			@NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal amount,
			@Size(max = 5000) String notes, Integer version) {

	}

	/**
	 * One line of a month's budget against actual: a department with a budget or with spend (or, with a null
	 * department, the spend no department was given). {@code budget} and its figures are null without a budget.
	 */
	public record BudgetLine(DepartmentSummary department, Long budgetId, BigDecimal budget, BigDecimal actual,
			BigDecimal remaining, BigDecimal usedPercent, boolean overBudget, BigDecimal overBy) {

	}

	public record BudgetSummary(int year, int month, List<BudgetLine> lines, BigDecimal totalBudget,
			BigDecimal totalActual, BigDecimal totalActualBudgeted, long overBudgetDepartments) {

	}

	// --- dashboard --------------------------------------------------------------------------------------------

	public record MonthSpend(int year, int month, BigDecimal totalAud) {

	}

	/** {@code department} null for spend no department was given. */
	public record DepartmentSpend(DepartmentSummary department, BigDecimal totalAud) {

	}

	public record CategorySpend(ExpenseCategory category, BigDecimal totalAud, long expenses) {

	}

	/**
	 * The Finance Tracker for a month (this month by default, on the business calendar). All amounts in AUD.
	 * @param changePercent this month against the previous one; null when the previous month had no spend
	 * @param trend the 12 months up to and including this one, months without spend at 0
	 * @param upcomingRenewals active and trial licences renewing in the next 30 days (the first 10 of the count)
	 * @param underUsedLicences licences with seats used below 70% of seats paid (the 10 least used of the count)
	 * @param annualisedSoftwareCostAud the annual cost of every active and trial licence at today's rates
	 * @param licencesWithoutRate licences left out of it because their currency has no rate yet
	 */
	public record FinanceDashboard(LocalDate asOf, int year, int month, BigDecimal monthSpendAud,
			BigDecimal previousMonthSpendAud, BigDecimal changePercent, long expenseCount, List<MonthSpend> trend,
			List<DepartmentSpend> byDepartment, List<CategorySpend> byCategory, BigDecimal monthBudgetAud,
			long overBudgetDepartments, long upcomingRenewalCount, List<LicenceDto> upcomingRenewals,
			long underUsedCount, List<LicenceDto> underUsedLicences, BigDecimal annualisedSoftwareCostAud,
			long licencesWithoutRate) {

	}

}
