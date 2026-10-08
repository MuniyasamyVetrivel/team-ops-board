package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.paid.repository.PaidCampaignQuery;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

import lombok.RequiredArgsConstructor;

/**
 * LinkedIn Campaigns: paid campaigns with results recorded in the month. Every month has a value; zero is a measured
 * result.
 */
@Component
@RequiredArgsConstructor
class PaidCampaignsActualSource implements TargetActualSource {

	private final PaidCampaignQuery campaignQuery;

	@Override
	public ActualSource source() {
		return ActualSource.PAID_CAMPAIGNS;
	}

	@Override
	public Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		Map<MarketingPeriod, PaidCampaignQuery.Totals> totals = campaignQuery.monthly(from, to, null, null);
		Map<MarketingPeriod, BigDecimal> actuals = new HashMap<>();
		for (MarketingPeriod p = from; !p.firstDay().isAfter(to.firstDay()); p = p.next()) {
			actuals.put(p, BigDecimal.valueOf(totals.getOrDefault(p, PaidCampaignQuery.Totals.EMPTY).campaigns()));
		}
		return actuals;
	}

}
