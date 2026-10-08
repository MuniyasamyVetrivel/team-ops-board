package com.teamops.marketing.seo.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.teamops.marketing.common.RankingChange;
import com.teamops.marketing.common.RankingStatus;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.seo.dto.SeoDtos.PageRef;
import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.entity.RankingSource;
import com.teamops.marketing.seo.entity.SearchEngine;
import com.teamops.marketing.seo.service.SeoStats;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Monthly keyword ranking records (brief sections 25, 27 and 29). Statuses and movements are computed. */
public final class RankingDtos {

	private RankingDtos() {
	}

	/**
	 * One recorded month. {@code change} compares with the calendar month before (NEW when it has no row).
	 * {@code correctable} is true while the month is still open for corrections.
	 */
	public record RankingEntry(Long id, int month, int year, String label, Integer position, RankingStatus status,
			RankingChange change, Integer searchVolume, String notes, RankingSource source, UserSummary recordedBy,
			Instant createdAt, Instant updatedAt, Integer version, boolean correctable) {

	}

	/** A keyword's whole ranking history, newest month first. */
	public record KeywordHistory(Long keywordId, String keyword, PageRef page, SearchEngine searchEngine,
			String location, Device device, Integer targetPosition, KeywordStatus status, List<RankingEntry> entries) {

	}

	/** Records a month that has no ranking yet. {@code position} {@code null} means Not Ranked. */
	public record RecordRanking(
			@NotNull(message = "Month is required") @Min(value = 1, message = "1–12") @Max(value = 12, message = "1–12") Integer month,
			@NotNull(message = "Year is required") @Min(value = 2000, message = "2000–2100") @Max(value = 2100, message = "2000–2100") Integer year,
			@Min(value = 1, message = "1–100") @Max(value = 100, message = "1–100") Integer position,
			@Min(value = 0, message = "Cannot be negative") @Max(value = 1_000_000_000, message = "Too large") Integer searchVolume,
			@Size(max = 1000, message = "At most 1000 characters") String notes) {

	}

	/** Corrects a recorded month (audited). {@code position} {@code null} means Not Ranked. */
	public record CorrectRanking(
			@NotNull(message = "Version is required") Integer version,
			@Min(value = 1, message = "1–100") @Max(value = 100, message = "1–100") Integer position,
			@Min(value = 0, message = "Cannot be negative") @Max(value = 1_000_000_000, message = "Too large") Integer searchVolume,
			@Size(max = 1000, message = "At most 1000 characters") String notes) {

	}

	public record MonthlyEntry(
			@NotNull(message = "Keyword is required") Long keywordId,
			@Min(value = 1, message = "1–100") @Max(value = 100, message = "1–100") Integer position,
			@Min(value = 0, message = "Cannot be negative") @Max(value = 1_000_000_000, message = "Too large") Integer searchVolume,
			@Size(max = 1000, message = "At most 1000 characters") String notes) {

	}

	/** The monthly SEO update: positions for many keywords in one month, all or nothing. */
	public record RecordMonthly(
			@NotNull(message = "Month is required") @Min(value = 1, message = "1–12") @Max(value = 12, message = "1–12") Integer month,
			@NotNull(message = "Year is required") @Min(value = 2000, message = "2000–2100") @Max(value = 2100, message = "2000–2100") Integer year,
			@NotEmpty(message = "Enter at least one ranking") @Size(max = 500, message = "At most 500 rankings at a time") List<@Valid @NotNull MonthlyEntry> entries) {

	}

	public record RecordResult(Period period, int recorded) {

	}

	/** The monthly SEO summary with the month before for comparison (brief section 29). */
	public record MonthlyReport(Period period, SeoStats stats, Period previousPeriod, SeoStats previousStats) {

	}

	/** A month in a history chart: {@code position} is {@code null} when not ranked or not recorded. */
	public record Point(boolean recorded, Integer position) {

	}

	public record KeywordSeries(Long keywordId, String keyword, Device device, List<Point> points) {

	}

	/**
	 * Positions for a page's keywords over consecutive months, oldest first. {@code averages} holds the mean ranked
	 * position for each month ({@code null} when nothing ranked).
	 */
	public record PageHistory(List<Period> periods, List<KeywordSeries> series, List<BigDecimal> averages) {

	}

}
