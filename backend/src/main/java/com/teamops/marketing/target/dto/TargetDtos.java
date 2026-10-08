package com.teamops.marketing.target.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.TargetProgress.TargetStatus;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetUnit;
import com.teamops.marketing.target.service.TargetRules.ActualOrigin;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Marketing target API records. Achievement %, remaining and status are computed per request. */
public final class TargetDtos {

	private TargetDtos() {
	}

	// --- types ----------------------------------------------------------------------------------------------

	/**
	 * A target type. {@code automatic} is true when the actual is computed now; otherwise it is entered by hand
	 * (MANUAL types, and sources whose module does not exist yet). {@code locked} is true once the type has targets:
	 * its unit and actual source can no longer change.
	 */
	public record TargetTypeItem(Long id, String code, String name, String description, TargetUnit unit,
			ActualSource actualSource, LeadSource leadSourceFilter, BigDecimal behindThresholdPct,
			BigDecimal effectiveThresholdPct, boolean automatic, boolean active, int position, long targetCount,
			boolean locked, Integer version) {

	}

	public record TypeRef(Long id, String code, String name, TargetUnit unit, ActualSource actualSource,
			boolean automatic) {

	}

	public record CreateTargetType(
			@Size(max = 50, message = "At most 50 characters")
			@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "Letters, digits and underscores, starting with a letter") String code,
			@NotBlank(message = "Name is required") @Size(max = 100, message = "At most 100 characters") String name,
			@Size(max = 500, message = "At most 500 characters") String description,
			@NotNull(message = "Unit is required") TargetUnit unit,
			@NotNull(message = "Actual source is required") ActualSource actualSource,
			LeadSource leadSourceFilter,
			@DecimalMin(value = "0", message = "0–100") @DecimalMax(value = "100", message = "0–100") @Digits(integer = 3, fraction = 2, message = "At most two decimals") BigDecimal behindThresholdPct,
			Integer position) {

	}

	/** Full replacement. Unit and actual source can only change while the type has no targets; the code is fixed. */
	public record UpdateTargetType(
			@NotNull(message = "Version is required") Integer version,
			@NotBlank(message = "Name is required") @Size(max = 100, message = "At most 100 characters") String name,
			@Size(max = 500, message = "At most 500 characters") String description,
			@NotNull(message = "Unit is required") TargetUnit unit,
			@NotNull(message = "Actual source is required") ActualSource actualSource,
			LeadSource leadSourceFilter,
			@DecimalMin(value = "0", message = "0–100") @DecimalMax(value = "100", message = "0–100") @Digits(integer = 3, fraction = 2, message = "At most two decimals") BigDecimal behindThresholdPct,
			@NotNull(message = "Active is required") Boolean active,
			@NotNull(message = "Position is required") @Min(value = 0, message = "Cannot be negative") Integer position) {

	}

	// --- targets --------------------------------------------------------------------------------------------

	/**
	 * A month's target with its computed progress. {@code actual} is {@code null} when nothing is recorded
	 * ({@code actualOrigin} NONE), which counts as zero for progress. {@code editable} says whether the viewer may
	 * still change the month; {@code actualEditable} whether the actual is entered by hand.
	 */
	public record TargetItem(Long id, TypeRef type, int month, int year, String label, BigDecimal targetValue,
			BigDecimal actual, ActualOrigin actualOrigin, BigDecimal achievementPct, BigDecimal remaining,
			TargetStatus status, BigDecimal thresholdPct, UserSummary owner, DepartmentSummary department, String notes,
			boolean editable, boolean actualEditable, Integer version, Instant createdAt, Instant updatedAt) {

	}

	public record StatusSummary(int total, int achieved, int inProgress, int behind) {

	}

	/** The target performance table for a month, plus the active types that have no target yet. */
	public record MonthlyTargets(Period period, List<TargetItem> targets, StatusSummary summary,
			List<TypeRef> typesWithoutTarget) {

	}

	private static final String MAX_VALUE = "999999999999.99";

	public record CreateTarget(
			@NotNull(message = "Target type is required") Long typeId,
			@NotNull(message = "Month is required") @Min(value = 1, message = "1–12") @Max(value = 12, message = "1–12") Integer month,
			@NotNull(message = "Year is required") @Min(value = 2000, message = "2000–2100") @Max(value = 2100, message = "2000–2100") Integer year,
			@NotNull(message = "Target is required") @DecimalMin(value = "0", inclusive = false, message = "Must be more than 0") @DecimalMax(value = MAX_VALUE, message = "Too large") @Digits(integer = 12, fraction = 2, message = "At most two decimals") BigDecimal targetValue,
			@DecimalMin(value = "0", message = "Cannot be negative") @DecimalMax(value = MAX_VALUE, message = "Too large") @Digits(integer = 12, fraction = 2, message = "At most two decimals") BigDecimal actualValue,
			Long ownerId,
			@NotNull(message = "Department is required") Long departmentId,
			@Size(max = 1000, message = "At most 1000 characters") String notes) {

	}

	/** Full replacement of a month's editable fields; type and month are fixed. */
	public record UpdateTarget(
			@NotNull(message = "Version is required") Integer version,
			@NotNull(message = "Target is required") @DecimalMin(value = "0", inclusive = false, message = "Must be more than 0") @DecimalMax(value = MAX_VALUE, message = "Too large") @Digits(integer = 12, fraction = 2, message = "At most two decimals") BigDecimal targetValue,
			@DecimalMin(value = "0", message = "Cannot be negative") @DecimalMax(value = MAX_VALUE, message = "Too large") @Digits(integer = 12, fraction = 2, message = "At most two decimals") BigDecimal actualValue,
			Long ownerId,
			@NotNull(message = "Department is required") Long departmentId,
			@Size(max = 1000, message = "At most 1000 characters") String notes) {

	}

	public record MonthlyTargetEntry(
			@NotNull(message = "Target type is required") Long typeId,
			@NotNull(message = "Target is required") @DecimalMin(value = "0", inclusive = false, message = "Must be more than 0") @DecimalMax(value = MAX_VALUE, message = "Too large") @Digits(integer = 12, fraction = 2, message = "At most two decimals") BigDecimal targetValue) {

	}

	/** Sets several types' targets for one month, all or nothing (409 if any already has the month). */
	public record SetMonthlyTargets(
			@NotNull(message = "Month is required") @Min(value = 1, message = "1–12") @Max(value = 12, message = "1–12") Integer month,
			@NotNull(message = "Year is required") @Min(value = 2000, message = "2000–2100") @Max(value = 2100, message = "2000–2100") Integer year,
			Long ownerId,
			@NotNull(message = "Department is required") Long departmentId,
			@NotEmpty(message = "Enter at least one target") @Size(max = 100, message = "At most 100 targets at a time") List<@Valid @NotNull MonthlyTargetEntry> entries) {

	}

	public record SetMonthlyResult(Period period, int created) {

	}

	// --- trend ----------------------------------------------------------------------------------------------

	public enum TrendView {

		MONTH, QUARTER, YEAR

	}

	/**
	 * One bucket of the trend: a month, a quarter or a year. Counts and money add up over the bucket's months with
	 * a target; percentages are averaged. {@code targetValue} is {@code null} when no month in it has a target.
	 */
	public record TrendPoint(String label, Period from, Period to, int months, BigDecimal targetValue,
			BigDecimal actual, BigDecimal achievementPct, BigDecimal remaining, TargetStatus status) {

	}

	public record TargetTrend(TypeRef type, TrendView view, BigDecimal thresholdPct, List<TrendPoint> points) {

	}

}
