package com.teamops.marketing.integration.manual;

import java.util.List;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.ProviderRecords.KeywordQuery;
import com.teamops.marketing.integration.ProviderRecords.RankingObservation;
import com.teamops.marketing.integration.SeoRankingProvider;

/** Manual entry and CSV import: nothing to fetch. */
@Component
public class ManualSeoRankingProvider implements SeoRankingProvider {

	private static final ProviderInfo INFO = new ProviderInfo("MANUAL", "Manual entry", Category.SEO_RANKINGS, false,
			false, "Positions are recorded monthly by the SEO team or imported from CSV.",
			List.of("Semrush", "Google Search Console", "Ahrefs"));

	@Override
	public ProviderInfo info() {
		return INFO;
	}

	@Override
	public List<RankingObservation> fetchRankings(MarketingPeriod period, List<KeywordQuery> keywords) {
		return List.of();
	}

}
