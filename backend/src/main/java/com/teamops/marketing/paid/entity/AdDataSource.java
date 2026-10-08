package com.teamops.marketing.paid.entity;

/**
 * Where a paid campaign or a month's results came from: typed in, a CSV import, or (later) the LinkedIn Ads API
 * through a {@code PaidCampaignProvider}.
 */
public enum AdDataSource {

	MANUAL, CSV, LINKEDIN_ADS

}
