package com.teamops.marketing.paid.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.paid.entity.AdPlatform;
import com.teamops.marketing.paid.service.PaidResults;

import lombok.RequiredArgsConstructor;

/** Summed paid results from grouped queries: per campaign (lifetime or one month), per month, per platform. */
@Repository
@RequiredArgsConstructor
public class PaidCampaignQuery {

	private static final String SUMS = """
			coalesce(sum(m.amount_spent), 0) as spend, coalesce(sum(m.impressions), 0) as impressions,
			coalesce(sum(m.clicks), 0) as clicks, coalesce(sum(m.leads), 0) as leads,
			coalesce(sum(m.conversions), 0) as conversions
			""";

	private final NamedParameterJdbcTemplate jdbc;

	/** Campaigns with results in a month (or range) and their summed results. */
	public record Totals(int campaigns, PaidResults results) {

		public static final Totals EMPTY = new Totals(0, PaidResults.ZERO);

	}

	/** Campaigns running in a month (not drafts): how many, their summed budgets and their spend to date. */
	public record Budget(int campaigns, BigDecimal budget, BigDecimal spent) {
	}

	/** The budget of the non-draft campaigns whose dates overlap the month, with each one's lifetime spend. */
	public Budget running(MarketingPeriod period, Long ownerId, AdPlatform platform) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("first", period.firstDay())
			.addValue("last", period.lastDay());
		String where = "c.status <> 'DRAFT' and c.start_date <= :last and (c.end_date is null or c.end_date >= :first)";
		if (ownerId != null) {
			where += " and c.owner_id = :ownerId";
			params.addValue("ownerId", ownerId);
		}
		if (platform != null) {
			where += " and c.platform = :platform";
			params.addValue("platform", platform.name());
		}
		return jdbc.queryForObject("select count(*) as campaigns, coalesce(sum(c.budget), 0) as budget,"
				+ " coalesce(sum(s.spent), 0) as spent from paid_campaigns c left join (select campaign_id,"
				+ " sum(amount_spent) as spent from paid_campaign_metrics group by campaign_id) s on s.campaign_id = c.id"
				+ " where " + where, params, (rs, i) -> new Budget(rs.getInt("campaigns"),
						rs.getBigDecimal("budget").setScale(2, java.math.RoundingMode.HALF_UP),
						rs.getBigDecimal("spent").setScale(2, java.math.RoundingMode.HALF_UP)));
	}

	/** Each campaign's results, lifetime when {@code period} is null, else for that month only. */
	public Map<Long, PaidResults> byCampaign(Collection<Long> campaignIds, MarketingPeriod period) {
		Map<Long, PaidResults> results = new HashMap<>();
		if (campaignIds.isEmpty()) {
			return results;
		}
		MapSqlParameterSource params = new MapSqlParameterSource("ids", campaignIds);
		String where = "m.campaign_id in (:ids)";
		if (period != null) {
			where += " and m.year = :year and m.month = :month";
			params.addValue("year", period.year()).addValue("month", period.month());
		}
		jdbc.query("select m.campaign_id, " + SUMS + " from paid_campaign_metrics m where " + where
				+ " group by m.campaign_id", params, rs -> {
			results.put(rs.getLong("campaign_id"), results(rs));
		});
		return results;
	}

	/** Totals per month from {@code from} to {@code to}, optionally for one owner and/or platform. */
	public Map<MarketingPeriod, Totals> monthly(MarketingPeriod from, MarketingPeriod to, Long ownerId, AdPlatform platform) {
		MapSqlParameterSource params = range(from, to, ownerId, platform);
		Map<MarketingPeriod, Totals> totals = new HashMap<>();
		jdbc.query("select m.year as y, m.month as mo, count(distinct m.campaign_id) as campaigns, " + SUMS
				+ " from paid_campaign_metrics m join paid_campaigns c on c.id = m.campaign_id where "
				+ filters(ownerId, platform) + " group by m.year, m.month", params, rs -> {
			totals.put(new MarketingPeriod(rs.getInt("mo"), rs.getInt("y")), new Totals(rs.getInt("campaigns"), results(rs)));
		});
		return totals;
	}

	/** One month's totals per platform. */
	public Map<AdPlatform, Totals> byPlatform(MarketingPeriod period, Long ownerId) {
		Map<AdPlatform, Totals> totals = new EnumMap<>(AdPlatform.class);
		jdbc.query("select c.platform, count(distinct m.campaign_id) as campaigns, " + SUMS
				+ " from paid_campaign_metrics m join paid_campaigns c on c.id = m.campaign_id where "
				+ filters(ownerId, null) + " group by c.platform", range(period, period, ownerId, null), rs -> {
			totals.put(AdPlatform.valueOf(rs.getString("platform")), new Totals(rs.getInt("campaigns"), results(rs)));
		});
		return totals;
	}

	private static MapSqlParameterSource range(MarketingPeriod from, MarketingPeriod to, Long ownerId, AdPlatform platform) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", key(from)).addValue("to", key(to));
		if (ownerId != null) {
			params.addValue("ownerId", ownerId);
		}
		if (platform != null) {
			params.addValue("platform", platform.name());
		}
		return params;
	}

	private static String filters(Long ownerId, AdPlatform platform) {
		return "m.year * 12 + m.month between :from and :to" + (ownerId == null ? "" : " and c.owner_id = :ownerId")
				+ (platform == null ? "" : " and c.platform = :platform");
	}

	private static int key(MarketingPeriod period) {
		return period.year() * 12 + period.month();
	}

	private static PaidResults results(ResultSet rs) throws SQLException {
		BigDecimal spend = rs.getBigDecimal("spend");
		return new PaidResults(spend, rs.getLong("impressions"), rs.getLong("clicks"), rs.getLong("leads"),
				rs.getLong("conversions"));
	}

}
