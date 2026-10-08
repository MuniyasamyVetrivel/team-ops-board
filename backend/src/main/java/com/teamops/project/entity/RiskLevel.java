package com.teamops.project.entity;

/** Risk probability and impact. Severity = probability rank × impact rank (1–9). */
public enum RiskLevel {

	LOW, MEDIUM, HIGH;

	public int rank() {
		return ordinal() + 1;
	}

}
