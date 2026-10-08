package com.teamops.marketing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.MarketingDataProvider.ProviderInfo;
import com.teamops.user.dto.UserSummary;

/** Shared Digital Marketing responses. */
public final class MarketingDtos {

	private MarketingDtos() {
	}

	public record Period(int month, int year, String label) {

		public static Period of(MarketingPeriod period) {
			return new Period(period.month(), period.year(), period.label());
		}

	}

	/**
	 * Everything the marketing filter bar needs: the business "today", the default period, the selectable years, and
	 * the people who can own marketing work.
	 */
	public record MarketingContext(LocalDate today, Period currentPeriod, List<Integer> years, List<UserSummary> owners,
			BigDecimal behindThresholdPct) {
	}

	public record Integrations(List<ProviderInfo> providers) {
	}

}
