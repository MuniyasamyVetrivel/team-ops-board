package com.teamops.sla.service;

/** SLA state of one deadline. Ordered from best to worst. */
public enum SlaState {

	ON_TRACK, WARNING, BREACHED;

	public static SlaState worst(SlaState a, SlaState b) {
		return a.compareTo(b) >= 0 ? a : b;
	}

}
