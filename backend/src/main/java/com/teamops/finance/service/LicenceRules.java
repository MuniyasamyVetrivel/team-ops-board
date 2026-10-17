package com.teamops.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.teamops.common.exception.ApiException;
import com.teamops.finance.entity.BillingCycle;
import com.teamops.finance.entity.LicenceStatus;

/**
 * Tools and licences, computed and never stored: seats used are the assigned users, utilisation is used ÷ paid × 100
 * (null without paid seats), a licence below 70% is under-used (brief), the annual cost is 12 × a monthly cost or a
 * yearly cost (one-off costs have none), and renewal reminders go out 30 and 7 days before the renewal date.
 * Payment methods are labels: never card numbers, at most the last 4 digits.
 */
public final class LicenceRules {

	/** Seats used below this share of seats paid make a licence under-used (brief: below 70%). */
	public static final BigDecimal UNDER_USED_PERCENT = new BigDecimal("70");

	/** Renewal reminder lead times, shortest first (brief: 30 and 7 days before). */
	public static final List<Integer> LEAD_DAYS = List.of(7, 30);

	public static final int MAX_SEATS = 100_000;

	/** The most digits a payment method label may hold in a row (the last 4 of a card). */
	static final int MAX_LABEL_DIGITS = 4;

	/** Runs of digits, allowing the spaces and dashes card numbers are written with. */
	private static final Pattern DIGIT_RUN = Pattern.compile("\\d(?:[\\s-]*\\d)*");

	private static final Pattern LAST4 = Pattern.compile("\\d{4}");

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private static final BigDecimal MONTHS = new BigDecimal("12");

	private LicenceRules() {
	}

	/** Paid seats not assigned yet; null without paid seats. Never negative (over-assignment is flagged instead). */
	public static Integer seatsFree(Integer seatsPaid, int seatsUsed) {
		return seatsPaid == null ? null : Math.max(seatsPaid - seatsUsed, 0);
	}

	/** Seats used ÷ seats paid × 100, one decimal; null without paid seats (division by zero gives null). */
	public static BigDecimal utilisationPercent(Integer seatsPaid, int seatsUsed) {
		if (seatsPaid == null || seatsPaid == 0) {
			return null;
		}
		return BigDecimal.valueOf(seatsUsed).multiply(HUNDRED).divide(BigDecimal.valueOf(seatsPaid), 1, RoundingMode.HALF_UP);
	}

	/** Below 70% of its paid seats, while not cancelled. */
	public static boolean underUsed(Integer seatsPaid, int seatsUsed, LicenceStatus status) {
		BigDecimal utilisation = utilisationPercent(seatsPaid, seatsUsed);
		return status != LicenceStatus.CANCELLED && utilisation != null && utilisation.compareTo(UNDER_USED_PERCENT) < 0;
	}

	/** More users assigned than seats paid for. */
	public static boolean overAssigned(Integer seatsPaid, int seatsUsed) {
		return seatsPaid != null && seatsUsed > seatsPaid;
	}

	/** The cost per year in the licence's currency: 12 × monthly, or yearly; null for a one-off or without a cost. */
	public static BigDecimal annualCost(BigDecimal cost, BillingCycle cycle) {
		if (cost == null) {
			return null;
		}
		return switch (cycle) {
			case MONTHLY -> cost.multiply(MONTHS).setScale(2, RoundingMode.HALF_UP);
			case YEARLY -> cost.setScale(2, RoundingMode.HALF_UP);
			case ONE_OFF -> null;
		};
	}

	/** Days from today to the renewal while it is today or later and the licence is not cancelled; null otherwise. */
	public static Long daysUntilRenewal(LocalDate renewalDate, LicenceStatus status, LocalDate today) {
		if (renewalDate == null || status == LicenceStatus.CANCELLED || renewalDate.isBefore(today)) {
			return null;
		}
		return ChronoUnit.DAYS.between(today, renewalDate);
	}

	/** The reminder a renewal {@code daysLeft} days away is due: the shortest lead time it is within. */
	public static int leadFor(long daysLeft) {
		return LEAD_DAYS.stream().filter(lead -> daysLeft <= lead).findFirst().orElseThrow();
	}

	/** 400 INVALID_DATES unless the renewal date falls on or after the start date. */
	public static void checkDates(LocalDate startDate, LocalDate renewalDate) {
		if (startDate != null && renewalDate != null && renewalDate.isBefore(startDate)) {
			throw ApiException.badRequest("INVALID_DATES", "The renewal date must be on or after the start date");
		}
	}

	/**
	 * 400 CARD_NUMBER_NOT_ALLOWED when the label holds more than 4 digits in a row (spaces and dashes included), as a
	 * card number would; 400 INVALID_CARD_LAST4 unless the last 4 digits are exactly 4 digits.
	 */
	public static void checkPaymentMethod(String label, String cardLast4) {
		if (label != null && hasLongDigitRun(label)) {
			throw ApiException.badRequest("CARD_NUMBER_NOT_ALLOWED",
					"Never store a card number. Use a label such as \"Corporate Amex\" and at most the last 4 digits.");
		}
		if (cardLast4 != null && !LAST4.matcher(cardLast4).matches()) {
			throw ApiException.badRequest("INVALID_CARD_LAST4", "Give exactly the last 4 digits of the card, or none");
		}
	}

	static boolean hasLongDigitRun(String text) {
		Matcher runs = DIGIT_RUN.matcher(text);
		while (runs.find()) {
			if (runs.group().replaceAll("[\\s-]", "").length() > MAX_LABEL_DIGITS) {
				return true;
			}
		}
		return false;
	}

}
