package com.teamops.marketing.common;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

import com.teamops.common.exception.ApiException;

/**
 * The reporting month every marketing figure is filtered by. Defaults come from the business calendar's today, never
 * the server or browser clock.
 */
public record MarketingPeriod(int month, int year) {

	public static final int MIN_YEAR = 2000;

	public static final int MAX_YEAR = 2100;

	public MarketingPeriod {
		if (month < 1 || month > 12) {
			throw ApiException.badRequest("INVALID_PERIOD", "Month must be between 1 and 12");
		}
		if (year < MIN_YEAR || year > MAX_YEAR) {
			throw ApiException.badRequest("INVALID_PERIOD", "Year must be between " + MIN_YEAR + " and " + MAX_YEAR);
		}
	}

	public static MarketingPeriod of(LocalDate date) {
		return new MarketingPeriod(date.getMonthValue(), date.getYear());
	}

	/** The requested month and year, each defaulting to today's. */
	public static MarketingPeriod resolve(Integer month, Integer year, LocalDate today) {
		return new MarketingPeriod(month == null ? today.getMonthValue() : month,
				year == null ? today.getYear() : year);
	}

	public MarketingPeriod previous() {
		return of(firstDay().minusMonths(1));
	}

	public LocalDate firstDay() {
		return LocalDate.of(year, month, 1);
	}

	public LocalDate lastDay() {
		return YearMonth.of(year, month).atEndOfMonth();
	}

	public int quarter() {
		return (month - 1) / 3 + 1;
	}

	/** "October 2026". */
	public String label() {
		return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
	}

}
