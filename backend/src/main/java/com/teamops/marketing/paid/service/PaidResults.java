package com.teamops.marketing.paid.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Paid campaign results: one month of one campaign, or a sum (a campaign's lifetime, a month across campaigns).
 * Counts are {@code long} so sums never overflow; the checks mirror the table's CHECK constraints.
 */
public record PaidResults(BigDecimal spend, long impressions, long clicks, long leads, long conversions) {

	public static final PaidResults ZERO = new PaidResults(BigDecimal.ZERO.setScale(2), 0, 0, 0, 0);

	public PaidResults {
		spend = spend == null ? BigDecimal.ZERO.setScale(2) : spend.setScale(2, RoundingMode.HALF_UP);
	}

	/** A problem with one figure; {@code field} uses the API's field names. */
	public record Problem(String field, String message) {
	}

	public List<Problem> problems() {
		List<Problem> problems = new ArrayList<>();
		if (clicks > impressions) {
			problems.add(new Problem("clicks", "Cannot be more than the impressions"));
		}
		if (conversions > leads) {
			problems.add(new Problem("conversions", "Cannot be more than the leads"));
		}
		return problems;
	}

	public PaidResults plus(PaidResults other) {
		return new PaidResults(spend.add(other.spend), impressions + other.impressions, clicks + other.clicks,
				leads + other.leads, conversions + other.conversions);
	}

}
