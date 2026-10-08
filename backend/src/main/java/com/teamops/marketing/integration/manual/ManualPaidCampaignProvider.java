package com.teamops.marketing.integration.manual;

import java.util.List;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.PaidCampaignProvider;
import com.teamops.marketing.integration.ProviderRecords.PaidCampaignMetrics;

/** Manual entry and CSV import: nothing to fetch. */
@Component
public class ManualPaidCampaignProvider implements PaidCampaignProvider {

	private static final ProviderInfo INFO = new ProviderInfo("MANUAL", "Manual entry", Category.PAID_CAMPAIGNS, false,
			false, "Monthly spend, impressions, clicks and leads are entered per campaign.",
			List.of("LinkedIn Ads"));

	@Override
	public ProviderInfo info() {
		return INFO;
	}

	@Override
	public List<PaidCampaignMetrics> fetchMetrics(MarketingPeriod period) {
		return List.of();
	}

}
