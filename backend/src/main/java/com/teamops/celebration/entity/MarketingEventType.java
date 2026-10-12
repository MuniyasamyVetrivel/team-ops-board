package com.teamops.celebration.entity;

public enum MarketingEventType {

	UPCOMING_EVENT(OccasionCategory.EVENT, "event"), WEBINAR(OccasionCategory.WEBINAR, "webinar"),
	EVENT_MARKETING(OccasionCategory.EVENT_MARKETING, "event marketing"),
	EMAIL_MARKETING(OccasionCategory.EMAIL_MARKETING, "email marketing");

	private final OccasionCategory category;

	private final String label;

	MarketingEventType(OccasionCategory category, String label) {
		this.category = category;
		this.label = label;
	}

	public OccasionCategory category() {
		return category;
	}

	/** Lower-case label for messages, e.g. "webinar". */
	public String label() {
		return label;
	}

}
