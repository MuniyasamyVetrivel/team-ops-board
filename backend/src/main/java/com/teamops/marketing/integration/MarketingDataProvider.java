package com.teamops.marketing.integration;

import java.util.List;

/**
 * A source of marketing data. Today every source is manual entry (and CSV import); a future integration (Semrush,
 * Google Search Console, Zoho Campaigns, LinkedIn Ads, Google Analytics) implements the matching sub-interface and
 * is registered as the {@code @Primary} bean, without changing the services that use it.
 */
public interface MarketingDataProvider {

	ProviderInfo info();

	/** Which kind of data a provider supplies. */
	enum Category {

		SEO_RANKINGS, EMAIL_CAMPAIGNS, PAID_CAMPAIGNS, ANALYTICS, LEADS

	}

	/**
	 * What the UI shows about a data source.
	 *
	 * @param automated whether the provider can fetch data by itself (false for manual entry)
	 * @param connected whether an automated provider is configured and reachable
	 * @param planned integrations the architecture is ready for, for display only
	 */
	record ProviderInfo(String code, String name, Category category, boolean automated, boolean connected,
			String description, List<String> planned) {

		public ProviderInfo {
			planned = List.copyOf(planned);
		}

	}

}
