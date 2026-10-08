package com.teamops.marketing.paid.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.paid.entity.AdDataSource;
import com.teamops.marketing.paid.entity.AdPlatform;
import com.teamops.marketing.paid.entity.CampaignObjective;
import com.teamops.marketing.paid.entity.PaidCampaignStatus;
import com.teamops.marketing.paid.service.BudgetProgress;
import com.teamops.marketing.paid.service.PaidRates;
import com.teamops.marketing.paid.service.PaidResults;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Paid campaign API records. Rates and budget progress are computed per request. */
public final class PaidCampaignDtos {

	private static final String MAX_MONEY = "999999999999.99";

	private static final int MAX_COUNT = 1_000_000_000;

	private PaidCampaignDtos() {
	}

	/** Summed results with their rates. */
	public record Figures(PaidResults results, PaidRates rates) {

		public static Figures of(PaidResults results) {
			return new Figures(results, PaidRates.of(results));
		}

	}

	/**
	 * A campaign with its lifetime figures and budget progress. {@code month} holds the figures of the month the list
	 * was filtered by ({@code null} without a month filter, zeros when nothing was recorded that month).
	 */
	public record CampaignItem(Long id, String name, AdPlatform platform, CampaignObjective objective,
			LocalDate startDate, LocalDate endDate, BigDecimal budget, String currency, UserSummary owner,
			PaidCampaignStatus status, String notes, AdDataSource provider, String externalId, Figures lifetime,
			BudgetProgress budgetProgress, Figures month, Integer version, Instant createdAt, Instant updatedAt) {

	}

	/** One recorded month. {@code correctable}: the viewer may still change it. */
	public record MonthRow(Long id, Period period, Figures figures, String notes, AdDataSource source,
			UserSummary recordedBy, Instant updatedAt, Integer version, boolean correctable) {

	}

	public record CampaignPermissions(boolean canEdit) {

	}

	/** A campaign with every recorded month, oldest first. */
	public record CampaignDetail(CampaignItem campaign, List<MonthRow> months, CampaignPermissions permissions) {

	}

	/** Create or (with {@code version}) update a campaign's plan. */
	public record SaveCampaign(
			Integer version,
			@NotBlank(message = "Name is required") @Size(max = 200, message = "At most 200 characters") String name,
			AdPlatform platform,
			@NotNull(message = "Objective is required") CampaignObjective objective,
			@NotNull(message = "Start date is required") LocalDate startDate,
			LocalDate endDate,
			@NotNull(message = "Budget is required") @DecimalMin(value = "0", inclusive = false, message = "Must be more than 0") @DecimalMax(value = MAX_MONEY, message = "Too large") @Digits(integer = 12, fraction = 2, message = "At most two decimals") BigDecimal budget,
			@Pattern(regexp = "^[A-Z]{3}$", message = "A three-letter currency code, e.g. INR") String currency,
			Long ownerId,
			@NotNull(message = "Status is required") PaidCampaignStatus status,
			@Size(max = 2000, message = "At most 2000 characters") String notes) {

	}

	/**
	 * A month's results: new when the month has none yet ({@code version} omitted), otherwise a correction of that
	 * month ({@code version} required).
	 */
	public record SaveMonth(
			Integer version,
			@NotNull(message = "Amount spent is required") @DecimalMin(value = "0", message = "Cannot be negative") @DecimalMax(value = MAX_MONEY, message = "Too large") @Digits(integer = 12, fraction = 2, message = "At most two decimals") BigDecimal amountSpent,
			@NotNull(message = "Impressions are required") @Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer impressions,
			@NotNull(message = "Clicks are required") @Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer clicks,
			@NotNull(message = "Leads are required") @Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer leads,
			@NotNull(message = "Conversions are required") @Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer conversions,
			@Size(max = 1000, message = "At most 1000 characters") String notes) {

		public PaidResults results() {
			return new PaidResults(amountSpent, impressions, clicks, leads, conversions);
		}

	}

	/** One month across campaigns: how many had results, the sums, and the rates of the sums. */
	public record MonthTotals(Period period, int campaigns, Figures figures) {

	}

	public record PlatformTotals(AdPlatform platform, int campaigns, Figures figures) {

	}

	/** The month against a comparison month (the previous one by default), plus the month per platform. */
	public record MonthlySummary(MonthTotals current, MonthTotals comparison, List<PlatformTotals> byPlatform) {

	}

	/** Spend vs leads (brief section 41), oldest month first; months without results have zeros. */
	public record PaidTrend(List<MonthTotals> months) {

	}

}
