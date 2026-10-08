package com.teamops.marketing.integration;

import java.util.List;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.ProviderRecords.KeywordQuery;
import com.teamops.marketing.integration.ProviderRecords.RankingObservation;

/** Monthly keyword positions. Later: Semrush, Google Search Console, Ahrefs. */
public interface SeoRankingProvider extends MarketingDataProvider {

	/** Positions for the given keywords in the month; keywords the provider does not know are left out. */
	List<RankingObservation> fetchRankings(MarketingPeriod period, List<KeywordQuery> keywords);

}
