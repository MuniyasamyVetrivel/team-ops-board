package com.teamops.marketing.backlink.entity;

/**
 * A backlink's stage (brief section 45). PROSPECTED has no dates; every later stage keeps the date it was reached, so
 * each month counts what was submitted, approved, went live, was rejected or was lost in it.
 */
public enum BacklinkStatus {

	PROSPECTED, SUBMITTED, APPROVED, LIVE, REJECTED, LOST

}
