package com.teamops.marketing.seo.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.RankingStatus;

/**
 * Monthly ranking rules (brief sections 25 and 56). History is insert-only per month: recording a month never touches
 * another month. A month's own row may be corrected only while the month is open, which is the current business month
 * and the month before it (rankings for a month are usually entered early in the next one). Older months are locked.
 */
public final class RankingRules {

	/** CSV and form spellings of "not ranked". */
	private static final Set<String> NOT_RANKED = Set.of("NR", "N/A", "NA", "NOT RANKED", "NOT_RANKED", "-");

	private RankingRules() {
	}

	/** Rankings describe the past: the current business month at the latest. */
	public static boolean isRecordable(MarketingPeriod period, LocalDate today) {
		return !period.firstDay().isAfter(MarketingPeriod.of(today).firstDay());
	}

	public static void requireRecordable(MarketingPeriod period, LocalDate today) {
		if (!isRecordable(period, today)) {
			throw ApiException.badRequest("FUTURE_PERIOD", "Rankings cannot be recorded for a future month");
		}
	}

	/** Whether a recorded month may still be corrected: the current business month or the one before it. */
	public static boolean isCorrectable(MarketingPeriod period, LocalDate today) {
		MarketingPeriod current = MarketingPeriod.of(today);
		return period.equals(current) || period.equals(current.previous());
	}

	/** A position from 1 to 100, or {@code null} for Not Ranked. */
	public static Integer requireValidPosition(Integer position) {
		if (position != null && (position < RankingStatus.MIN_POSITION || position > RankingStatus.MAX_POSITION)) {
			throw ApiException.badRequest("INVALID_POSITION", "Position must be between 1 and 100, or not ranked");
		}
		return position;
	}

	/**
	 * Reads a position cell: a whole number from 1 to 100, or "NR" (also "N/A", "Not ranked", "-") for Not Ranked.
	 * Blank is not accepted, so a missing value is never silently stored as Not Ranked.
	 *
	 * @return the parsed cell, or {@code null} when the text is not a valid position
	 */
	public static ParsedPosition parsePosition(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String value = text.strip().toUpperCase(Locale.ROOT);
		if (NOT_RANKED.contains(value)) {
			return new ParsedPosition(null);
		}
		try {
			int position = Integer.parseInt(value.startsWith("#") ? value.substring(1) : value);
			return position >= RankingStatus.MIN_POSITION && position <= RankingStatus.MAX_POSITION
					? new ParsedPosition(position) : null;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	/** A parsed position cell; {@code position} is {@code null} for Not Ranked. */
	public record ParsedPosition(Integer position) {
	}

}
