package com.teamops.marketing.target.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingPeriod;

/** Lead target actuals: every month of the range gets a value, and a month without leads is a measured zero. */
class LeadCountsTest {

	@Test
	void sumsTheSourcesOfEachMonthAndFillsEmptyMonthsWithZero() {
		MarketingPeriod august = new MarketingPeriod(8, 2026);
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		// Brief section 44: October 2026, 200 leads from five sources.
		Map<MarketingPeriod, Map<LeadSource, Long>> counts = Map.of(october, Map.of(LeadSource.ORGANIC, 80L,
				LeadSource.EMAIL, 45L, LeadSource.LINKEDIN, 35L, LeadSource.BLOG, 20L, LeadSource.PAID_CAMPAIGN, 20L));

		Map<MarketingPeriod, BigDecimal> actuals = LeadCounts.perMonth(counts, august, october);

		assertThat(actuals).hasSize(3);
		assertThat(actuals.get(october)).isEqualByComparingTo("200");
		assertThat(actuals.get(new MarketingPeriod(9, 2026))).isEqualByComparingTo("0");
		assertThat(actuals.get(august)).isEqualByComparingTo("0");
	}

}
