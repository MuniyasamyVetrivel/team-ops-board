package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.Map;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

/**
 * Aggregates the actual of an automatic target type from the underlying records. Register an implementation as a
 * bean when the module that holds the records exists (leads, backlinks, content, campaigns); until then, types with
 * that source take a hand-entered actual.
 */
public interface TargetActualSource {

	ActualSource source();

	/**
	 * Actuals for every month from {@code from} to {@code to} inclusive. A month is missing (or {@code null}) when
	 * there is no data for it, as opposed to a measured zero.
	 */
	Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to);

}
