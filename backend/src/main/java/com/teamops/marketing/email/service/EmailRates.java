package com.teamops.marketing.email.service;

import java.math.BigDecimal;

import com.teamops.marketing.common.MarketingMath;

/**
 * Email campaign rates (brief sections 38 and 80), all percentages to two decimals and {@code null} when the
 * denominator is zero ("—"). Over several campaigns they are computed from the summed counts, so bigger sends weigh
 * more than an average of averages would.
 *
 * @param deliveryRate delivered ÷ sent
 * @param openRate unique opens ÷ delivered (8,500 ÷ 24,000 = 35.42)
 * @param clickRate unique clicks ÷ delivered
 * @param clickToOpenRate unique clicks ÷ unique opens
 * @param leadConversionRate leads ÷ delivered
 * @param bounceRate bounced ÷ sent
 * @param unsubscribeRate unsubscribed ÷ delivered
 */
public record EmailRates(BigDecimal deliveryRate, BigDecimal openRate, BigDecimal clickRate,
		BigDecimal clickToOpenRate, BigDecimal leadConversionRate, BigDecimal bounceRate, BigDecimal unsubscribeRate) {

	public static EmailRates of(EmailCounts c) {
		return new EmailRates(MarketingMath.percent(c.delivered(), c.emailsSent()),
				MarketingMath.openRate(c.uniqueOpens(), c.delivered()),
				MarketingMath.clickRate(c.uniqueClicks(), c.delivered()),
				MarketingMath.percent(c.uniqueClicks(), c.uniqueOpens()),
				MarketingMath.leadConversion(c.leads(), c.delivered()),
				MarketingMath.percent(c.bounced(), c.emailsSent()),
				MarketingMath.percent(c.unsubscribed(), c.delivered()));
	}

}
