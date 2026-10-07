package com.teamops.workload;

/** Brief section 9: 0–40 LOW, 41–70 NORMAL, 71–100 HIGH, 101+ OVERLOADED (on the rounded percentage). */
public enum WorkloadLevel {

	LOW, NORMAL, HIGH, OVERLOADED;

	public static WorkloadLevel of(int percent) {
		if (percent <= 40) {
			return LOW;
		}
		if (percent <= 70) {
			return NORMAL;
		}
		return percent <= 100 ? HIGH : OVERLOADED;
	}

}
