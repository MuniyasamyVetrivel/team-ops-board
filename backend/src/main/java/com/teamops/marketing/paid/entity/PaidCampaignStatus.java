package com.teamops.marketing.paid.entity;

/** A DRAFT campaign has not run, so it has no monthly results; the other statuses can. */
public enum PaidCampaignStatus {

	DRAFT, ACTIVE, PAUSED, COMPLETED

}
