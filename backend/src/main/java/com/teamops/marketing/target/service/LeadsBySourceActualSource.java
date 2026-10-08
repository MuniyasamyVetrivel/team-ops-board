package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.lead.repository.LeadQuery;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

import lombok.RequiredArgsConstructor;

/**
 * Organic, Blog, Email, LinkedIn and Paid Campaign Leads: leads of the type's lead source dated in the month. A type
 * without a lead source has no computed actual.
 */
@Component
@RequiredArgsConstructor
class LeadsBySourceActualSource implements TargetActualSource {

	private final LeadQuery leadQuery;

	@Override
	public ActualSource source() {
		return ActualSource.LEADS_BY_SOURCE;
	}

	@Override
	public Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		if (type.getLeadSourceFilter() == null) {
			return Map.of();
		}
		return LeadCounts.perMonth(leadQuery.monthly(from, to, null, type.getLeadSourceFilter()), from, to);
	}

}
