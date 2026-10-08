package com.teamops.marketing.integration;

import java.util.List;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.ProviderRecords.PaidCampaignMetrics;

/** Paid campaign spend and results for a month. Later: LinkedIn Ads. */
public interface PaidCampaignProvider extends MarketingDataProvider {

	List<PaidCampaignMetrics> fetchMetrics(MarketingPeriod period);

}
