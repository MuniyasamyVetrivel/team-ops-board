package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.email.repository.EmailCampaignQuery;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

import lombok.RequiredArgsConstructor;

/** Email Campaigns: campaigns SENT in the month (by campaign date). Every month has a value; zero is a measured result. */
@Component
@RequiredArgsConstructor
class EmailCampaignsActualSource implements TargetActualSource {

	private final EmailCampaignQuery campaignQuery;

	@Override
	public ActualSource source() {
		return ActualSource.EMAIL_CAMPAIGNS;
	}

	@Override
	public Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		Map<MarketingPeriod, EmailCampaignQuery.Totals> totals = campaignQuery.monthly(from, to, null);
		Map<MarketingPeriod, BigDecimal> actuals = new HashMap<>();
		for (MarketingPeriod p = from; !p.firstDay().isAfter(to.firstDay()); p = p.next()) {
			actuals.put(p, BigDecimal.valueOf(totals.getOrDefault(p, EmailCampaignQuery.Totals.EMPTY).campaigns()));
		}
		return actuals;
	}

}
