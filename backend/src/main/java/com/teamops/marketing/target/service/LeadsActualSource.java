package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.lead.repository.LeadQuery;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

import lombok.RequiredArgsConstructor;

/** Website Leads: every marketing lead dated in the month, any source or status. Zero is a measured result. */
@Component
@RequiredArgsConstructor
class LeadsActualSource implements TargetActualSource {

	private final LeadQuery leadQuery;

	@Override
	public ActualSource source() {
		return ActualSource.LEADS;
	}

	@Override
	public Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		return LeadCounts.perMonth(leadQuery.monthly(from, to, null, null), from, to);
	}

}

/** Fills every month of a range from grouped lead counts (summing the sources present). */
final class LeadCounts {

	private LeadCounts() {
	}

	static Map<MarketingPeriod, BigDecimal> perMonth(Map<MarketingPeriod, Map<LeadSource, Long>> counts,
			MarketingPeriod from, MarketingPeriod to) {
		Map<MarketingPeriod, BigDecimal> actuals = new HashMap<>();
		for (MarketingPeriod p = from; !p.firstDay().isAfter(to.firstDay()); p = p.next()) {
			long leads = counts.getOrDefault(p, Map.of()).values().stream().mapToLong(Long::longValue).sum();
			actuals.put(p, BigDecimal.valueOf(leads));
		}
		return actuals;
	}

}
