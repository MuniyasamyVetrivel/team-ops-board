package com.teamops.marketing.integration.manual;

import java.util.List;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.AnalyticsProvider;
import com.teamops.marketing.integration.ProviderRecords.PageTraffic;

/** Manual entry and CSV import: nothing to fetch. */
@Component
public class ManualAnalyticsProvider implements AnalyticsProvider {

	private static final ProviderInfo INFO = new ProviderInfo("MANUAL", "Manual entry", Category.ANALYTICS, false,
			false, "Organic traffic and CTA clicks are entered on each content item.",
			List.of("Google Analytics"));

	@Override
	public ProviderInfo info() {
		return INFO;
	}

	@Override
	public List<PageTraffic> fetchTraffic(MarketingPeriod period, List<String> urls) {
		return List.of();
	}

}
