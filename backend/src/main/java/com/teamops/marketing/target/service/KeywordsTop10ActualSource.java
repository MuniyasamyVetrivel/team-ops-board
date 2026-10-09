package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.seo.repository.SeoRankingQuery;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

import lombok.RequiredArgsConstructor;

/**
 * Keywords in Top 10: keywords (not archived) recorded at positions 1–10 for the month, the same count as the SEO
 * monthly summary. A month with no rankings recorded at all has no actual yet.
 */
@Component
@RequiredArgsConstructor
class KeywordsTop10ActualSource implements TargetActualSource {

	private final SeoRankingQuery rankingQuery;

	@Override
	public ActualSource source() {
		return ActualSource.KEYWORDS_TOP10;
	}

	@Override
	public Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		Map<MarketingPeriod, BigDecimal> actuals = new HashMap<>();
		rankingQuery.top10ByMonth(from, to, null).forEach((period, top10) -> actuals.put(period, BigDecimal.valueOf(top10)));
		return actuals;
	}

}
