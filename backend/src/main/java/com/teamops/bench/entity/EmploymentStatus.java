package com.teamops.bench.entity;

/**
 * Whether a consultant can be allocated at all, as set by HR. The allocation side of a consultant's status (allocated,
 * partially allocated, on bench) is computed from allocations and never stored ({@code ConsultantStatus}).
 */
public enum EmploymentStatus {

	ACTIVE, LEAVE_OF_ABSENCE, NOTICE_PERIOD, LEFT

}
