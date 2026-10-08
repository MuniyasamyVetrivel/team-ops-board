package com.teamops.marketing.email.entity;

/** Only SENT campaigns carry counts and appear in monthly figures. */
public enum EmailCampaignStatus {

	DRAFT, SCHEDULED, SENT, CANCELLED

}
