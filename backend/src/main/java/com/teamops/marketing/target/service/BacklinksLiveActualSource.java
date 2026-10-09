package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.marketing.backlink.repository.BacklinkQuery;
import com.teamops.marketing.backlink.repository.BacklinkQuery.StageCounts;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

import lombok.RequiredArgsConstructor;

/**
 * Backlinks: links that went live in the month (by live date, whatever happened to them later, so closed months keep
 * their count). Zero is a measured result.
 */
@Component
@RequiredArgsConstructor
class BacklinksLiveActualSource implements TargetActualSource {

	private final BacklinkQuery backlinkQuery;

	@Override
	public ActualSource source() {
		return ActualSource.BACKLINKS_LIVE;
	}

	@Override
	public Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		Map<MarketingPeriod, StageCounts> counts = backlinkQuery.monthly(from, to, null);
		Map<MarketingPeriod, BigDecimal> actuals = new HashMap<>();
		for (MarketingPeriod p = from; !p.firstDay().isAfter(to.firstDay()); p = p.next()) {
			actuals.put(p, BigDecimal.valueOf(counts.getOrDefault(p, StageCounts.ZERO).live()));
		}
		return actuals;
	}

}
