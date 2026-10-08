package com.teamops.marketing.integration.manual;

import java.util.List;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.EmailCampaignProvider;
import com.teamops.marketing.integration.ProviderRecords.EmailCampaignMetrics;

/** Manual entry and CSV import: nothing to fetch. */
@Component
public class ManualEmailCampaignProvider implements EmailCampaignProvider {

	private static final ProviderInfo INFO = new ProviderInfo("MANUAL", "Manual entry", Category.EMAIL_CAMPAIGNS, false,
			false, "Campaign counts are entered after each send or imported from CSV.",
			List.of("Zoho Campaigns"));

	@Override
	public ProviderInfo info() {
		return INFO;
	}

	@Override
	public List<EmailCampaignMetrics> fetchCampaigns(MarketingPeriod period) {
		return List.of();
	}

}
