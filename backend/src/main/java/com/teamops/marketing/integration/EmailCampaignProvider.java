package com.teamops.marketing.integration;

import java.util.List;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.ProviderRecords.EmailCampaignMetrics;

/** Email campaign counts for campaigns sent in a month. Later: Zoho Campaigns. */
public interface EmailCampaignProvider extends MarketingDataProvider {

	List<EmailCampaignMetrics> fetchCampaigns(MarketingPeriod period);

}
