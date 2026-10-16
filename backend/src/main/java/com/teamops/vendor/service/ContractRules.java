package com.teamops.vendor.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.teamops.common.exception.ApiException;
import com.teamops.vendor.entity.VendorContract;

/**
 * Contract dates, computed and never stored. A contract comes up for renewal on its renewal date, or on its end date
 * when it has no renewal date (an expiring contract needs the same decision). The last day to give notice is that date
 * less the notice period. Renewal reminders go out 30 and 7 days before.
 */
public final class ContractRules {

	/** Renewal reminder lead times, shortest first (brief: 30 and 7 days before). */
	public static final List<Integer> LEAD_DAYS = List.of(7, 30);

	public static final int MAX_NOTICE_DAYS = 730;

	private ContractRules() {
	}

	/**
	 * Where a contract stands on a business day: UPCOMING before the start date, EXPIRED after the end date, ACTIVE in
	 * between (both days included; without an end date it stays active).
	 */
	public enum ContractStatus {

		UPCOMING, ACTIVE, EXPIRED;

		public static ContractStatus of(LocalDate startDate, LocalDate endDate, LocalDate today) {
			if (today.isBefore(startDate)) {
				return UPCOMING;
			}
			if (endDate != null && today.isAfter(endDate)) {
				return EXPIRED;
			}
			return ACTIVE;
		}

	}

	public static ContractStatus status(VendorContract contract, LocalDate today) {
		return ContractStatus.of(contract.getStartDate(), contract.getEndDate(), today);
	}

	/** The date the contract comes up for renewal: its renewal date, or else its end date; null when it has neither. */
	public static LocalDate renewsOn(LocalDate renewalDate, LocalDate endDate) {
		return renewalDate != null ? renewalDate : endDate;
	}

	public static LocalDate renewsOn(VendorContract contract) {
		return renewsOn(contract.getRenewalDate(), contract.getEndDate());
	}

	/** The last day to give notice before the renewal; null without a renewal date or a notice period. */
	public static LocalDate noticeBy(LocalDate renewsOn, Integer noticePeriodDays) {
		return renewsOn == null || noticePeriodDays == null ? null : renewsOn.minusDays(noticePeriodDays);
	}

	/**
	 * Days from today to the renewal while the contract has not expired and the renewal is today or later; null
	 * otherwise (nothing left to decide).
	 */
	public static Long daysUntilRenewal(VendorContract contract, LocalDate today) {
		LocalDate renewsOn = renewsOn(contract);
		if (renewsOn == null || renewsOn.isBefore(today) || status(contract, today) == ContractStatus.EXPIRED) {
			return null;
		}
		return ChronoUnit.DAYS.between(today, renewsOn);
	}

	/** The reminder a renewal {@code daysLeft} days away is due: the shortest lead time it is within. */
	public static int leadFor(long daysLeft) {
		return LEAD_DAYS.stream().filter(lead -> daysLeft <= lead).findFirst().orElseThrow();
	}

	/** 400 INVALID_DATES unless the end and the renewal date fall on or after the start date. */
	public static void checkDates(LocalDate startDate, LocalDate endDate, LocalDate renewalDate) {
		if (endDate != null && endDate.isBefore(startDate)) {
			throw ApiException.badRequest("INVALID_DATES", "The end date must be on or after the start date");
		}
		if (renewalDate != null && renewalDate.isBefore(startDate)) {
			throw ApiException.badRequest("INVALID_DATES", "The renewal date must be on or after the start date");
		}
	}

}
