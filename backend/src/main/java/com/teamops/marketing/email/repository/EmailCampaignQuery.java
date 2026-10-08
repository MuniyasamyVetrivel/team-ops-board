package com.teamops.marketing.email.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.email.entity.EmailCampaignType;
import com.teamops.marketing.email.service.EmailCounts;

import lombok.RequiredArgsConstructor;

/**
 * Monthly email figures from one grouped query: SENT campaigns only, a campaign counting in the month of its
 * campaign date. Months without a sent campaign are simply absent.
 */
@Repository
@RequiredArgsConstructor
public class EmailCampaignQuery {

	private static final String SUMS = """
			count(*) as campaigns, coalesce(sum(emails_sent), 0) as emails_sent, coalesce(sum(delivered), 0) as delivered,
			coalesce(sum(bounced), 0) as bounced, coalesce(sum(opened), 0) as opened,
			coalesce(sum(unique_opens), 0) as unique_opens, coalesce(sum(clicked), 0) as clicked,
			coalesce(sum(unique_clicks), 0) as unique_clicks, coalesce(sum(unsubscribed), 0) as unsubscribed,
			coalesce(sum(leads_generated), 0) as leads
			""";

	private final NamedParameterJdbcTemplate jdbc;

	/** Campaigns sent and their summed counts. */
	public record Totals(int campaigns, EmailCounts counts) {

		public static final Totals EMPTY = new Totals(0, EmailCounts.ZERO);

	}

	/** Totals per month from {@code from} to {@code to} inclusive, optionally for one owner. */
	public Map<MarketingPeriod, Totals> monthly(MarketingPeriod from, MarketingPeriod to, Long ownerId) {
		MapSqlParameterSource params = range(from, to, ownerId);
		Map<MarketingPeriod, Totals> totals = new HashMap<>();
		jdbc.query("select year(campaign_date) as y, month(campaign_date) as m, " + SUMS + """
				from email_campaigns
				where status = 'SENT' and campaign_date between :start and :end
				""" + ownerFilter(ownerId) + " group by year(campaign_date), month(campaign_date)", params, rs -> {
			totals.put(new MarketingPeriod(rs.getInt("m"), rs.getInt("y")), totals(rs));
		});
		return totals;
	}

	/** One month's totals per campaign type. */
	public Map<EmailCampaignType, Totals> byType(MarketingPeriod period, Long ownerId) {
		Map<EmailCampaignType, Totals> totals = new EnumMap<>(EmailCampaignType.class);
		jdbc.query("select campaign_type, " + SUMS + """
				from email_campaigns
				where status = 'SENT' and campaign_date between :start and :end
				""" + ownerFilter(ownerId) + " group by campaign_type", range(period, period, ownerId), rs -> {
			totals.put(EmailCampaignType.valueOf(rs.getString("campaign_type")), totals(rs));
		});
		return totals;
	}

	private static MapSqlParameterSource range(MarketingPeriod from, MarketingPeriod to, Long ownerId) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("start", Date.valueOf(from.firstDay()))
			.addValue("end", Date.valueOf(to.lastDay()));
		if (ownerId != null) {
			params.addValue("ownerId", ownerId);
		}
		return params;
	}

	private static String ownerFilter(Long ownerId) {
		return ownerId == null ? "" : " and owner_id = :ownerId";
	}

	private static Totals totals(ResultSet rs) throws SQLException {
		return new Totals(rs.getInt("campaigns"),
				new EmailCounts(rs.getInt("emails_sent"), rs.getInt("delivered"), rs.getInt("bounced"),
						rs.getInt("opened"), rs.getInt("unique_opens"), rs.getInt("clicked"), rs.getInt("unique_clicks"),
						rs.getInt("unsubscribed"), rs.getInt("leads")));
	}

}
