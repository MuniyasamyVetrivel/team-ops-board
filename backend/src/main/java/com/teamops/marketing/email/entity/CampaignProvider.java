package com.teamops.marketing.email.entity;

/**
 * Where a campaign's figures came from. MANUAL is typed in; CSV is a file import (its external id, e.g. the Zoho
 * campaign id, stops the same campaign being imported twice); ZOHO is reserved for the Zoho Campaigns integration.
 */
public enum CampaignProvider {

	MANUAL, CSV, ZOHO

}
