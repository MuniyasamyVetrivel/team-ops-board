package com.teamops.marketing.common;

import java.time.LocalDate;

/**
 * The marketing rule for monthly figures: a month that has not started cannot have results; a recorded month can be
 * corrected while it is open (the current business month or the one before), and a Super Admin can correct any past
 * month. New past months can always be added; they never overwrite another month.
 */
public final class MarketingMonths {

	private MarketingMonths() {
	}

	public static boolean isFuture(MarketingPeriod period, LocalDate today) {
		return period.firstDay().isAfter(MarketingPeriod.of(today).firstDay());
	}

	/** The current business month or the one before it. */
	public static boolean isOpen(MarketingPeriod period, LocalDate today) {
		MarketingPeriod current = MarketingPeriod.of(today);
		return period.equals(current) || period.equals(current.previous());
	}

	public static boolean canCorrect(MarketingPeriod period, LocalDate today, boolean superAdmin) {
		return !isFuture(period, today) && (superAdmin || isOpen(period, today));
	}

}
