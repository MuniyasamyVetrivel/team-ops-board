package com.teamops.marketing.integration;

import java.util.List;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.ProviderRecords.PageTraffic;

/** Website traffic per page for a month. Later: Google Analytics. */
public interface AnalyticsProvider extends MarketingDataProvider {

	List<PageTraffic> fetchTraffic(MarketingPeriod period, List<String> urls);

}
