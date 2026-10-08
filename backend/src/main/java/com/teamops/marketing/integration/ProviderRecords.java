package com.teamops.marketing.integration;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Provider-neutral records exchanged with external marketing systems. They carry raw counts only; rates (open rate,
 * CTR, CPL, …) are always computed by {@code MarketingMath}, never taken from a provider.
 */
public final class ProviderRecords {

	private ProviderRecords() {
	}

	/** A keyword to look up. {@code device} is DESKTOP or MOBILE. */
	public record KeywordQuery(String keyword, String url, String searchEngine, String location, String device) {
	}

	/** A keyword's position for a month; {@code position} is {@code null} when it is not ranked. */
	public record RankingObservation(KeywordQuery query, Integer position, Integer searchVolume) {
	}

	public record EmailCampaignMetrics(String externalId, String name, LocalDate sentOn, long emailsSent,
			long delivered, long bounced, long uniqueOpens, long uniqueClicks, long unsubscribed, long leads) {
	}

	public record PaidCampaignMetrics(String externalId, String name, String platform, BigDecimal amountSpent,
			String currency, long impressions, long clicks, long leads, long conversions) {
	}

	public record PageTraffic(String url, long sessions, long users, long conversions) {
	}

	public record ExternalLead(String externalId, String name, String company, String email, String phone,
			String source, LocalDate leadDate) {
	}

}
