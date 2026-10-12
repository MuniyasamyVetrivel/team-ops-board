package com.teamops.celebration.entity;

public enum LinkedInPostStatus {

	NOT_STARTED("Not started"), DRAFTED("Drafted"), SCHEDULED("Scheduled"), POSTED("Posted");

	private final String label;

	LinkedInPostStatus(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
