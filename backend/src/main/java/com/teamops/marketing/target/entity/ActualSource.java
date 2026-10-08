package com.teamops.marketing.target.entity;

/**
 * Where a target's actual comes from. MANUAL is entered by hand; every other source is aggregated from the
 * underlying records by a {@code TargetActualSource} bean once the module that holds them exists.
 */
public enum ActualSource {

	MANUAL,
	/** Every marketing lead dated in the month. */
	LEADS,
	/** Leads of the type's {@code leadSourceFilter} dated in the month. */
	LEADS_BY_SOURCE,
	/** Backlinks that went live in the month. */
	BACKLINKS_LIVE,
	/** Blog posts published in the month. */
	BLOGS_PUBLISHED,
	/** Keywords (not archived) recorded at positions 1–10 for the month. */
	KEYWORDS_TOP10,
	/** Email campaigns sent in the month. */
	EMAIL_CAMPAIGNS,
	/** Paid campaigns running in the month. */
	PAID_CAMPAIGNS,
	/** SEO pages of type LANDING_PAGE created in the month (business time zone). */
	LANDING_PAGES

}
