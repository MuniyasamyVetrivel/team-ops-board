package com.teamops.bench.service;

import com.teamops.bench.entity.EmploymentStatus;

/**
 * A consultant's status as the brief lists it (Allocated, Partially allocated, On bench, On leave of absence, Notice
 * period, Left). Computed, never stored: HR's employment status wins (someone on leave, on notice or gone is not
 * available, whatever their allocations say); an active consultant is allocated at 100% or more, partially allocated
 * above 0%, and on the bench otherwise.
 */
public enum ConsultantStatus {

	ALLOCATED, PARTIALLY_ALLOCATED, ON_BENCH, LEAVE_OF_ABSENCE, NOTICE_PERIOD, LEFT;

	public static ConsultantStatus of(EmploymentStatus employment, int allocatedPercent) {
		return switch (employment) {
			case LEFT -> LEFT;
			case LEAVE_OF_ABSENCE -> LEAVE_OF_ABSENCE;
			case NOTICE_PERIOD -> NOTICE_PERIOD;
			case ACTIVE -> allocatedPercent >= 100 ? ALLOCATED : allocatedPercent > 0 ? PARTIALLY_ALLOCATED : ON_BENCH;
		};
	}

	/** The employment status this status requires, or null when it depends on allocations. */
	public EmploymentStatus employmentStatus() {
		return switch (this) {
			case LEFT -> EmploymentStatus.LEFT;
			case LEAVE_OF_ABSENCE -> EmploymentStatus.LEAVE_OF_ABSENCE;
			case NOTICE_PERIOD -> EmploymentStatus.NOTICE_PERIOD;
			case ALLOCATED, PARTIALLY_ALLOCATED, ON_BENCH -> null;
		};
	}

}
