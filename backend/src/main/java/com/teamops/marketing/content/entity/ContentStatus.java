package com.teamops.marketing.content.entity;

/** A content item's stage (brief section 46). PUBLISHED and UPDATED items are live and have a publication date. */
public enum ContentStatus {

	IDEA, PLANNED, IN_PROGRESS, DRAFT, PUBLISHED, UPDATED;

	public boolean isLive() {
		return this == PUBLISHED || this == UPDATED;
	}

}
