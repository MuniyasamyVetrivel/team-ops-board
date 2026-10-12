package com.teamops.celebration.entity;

import java.util.Locale;

public enum Region {

	AUSTRALIA, INDIA, GLOBAL;

	/** "Australia", "India", "Global". */
	public String label() {
		return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
	}

}
