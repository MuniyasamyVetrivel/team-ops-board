package com.teamops.marketing.paid.service;

import java.math.BigDecimal;

import com.teamops.marketing.common.MarketingMath;

/**
 * Paid campaign rates (brief sections 41 and 80), {@code null} when the denominator is zero ("—"). Over several
 * campaigns or months they come from the summed results, so they are weighted, never averaged.
 *
 * @param ctr clicks ÷ impressions × 100 (2,800 ÷ 150,000 = 1.87)
 * @param costPerLead spend ÷ leads (₹42,000 ÷ 84 = ₹500.00)
 * @param conversionRate conversions ÷ leads × 100
 * @param costPerClick spend ÷ clicks
 */
public record PaidRates(BigDecimal ctr, BigDecimal costPerLead, BigDecimal conversionRate, BigDecimal costPerClick) {

	public static PaidRates of(PaidResults r) {
		return new PaidRates(MarketingMath.clickThroughRate(r.clicks(), r.impressions()),
				MarketingMath.costPerLead(r.spend(), r.leads()), MarketingMath.conversionRate(r.conversions(), r.leads()),
				MarketingMath.perUnit(r.spend(), r.clicks()));
	}

}
