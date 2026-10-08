package com.teamops.marketing.lead.repository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.lead.entity.LeadStatus;

import lombok.RequiredArgsConstructor;

/** Lead counts from grouped queries: per month and source, per status, and per linked campaign or content. */
@Repository
@RequiredArgsConstructor
public class LeadQuery {

	private final NamedParameterJdbcTemplate jdbc;

	/** What brought leads in: an email campaign, a paid campaign or a content item. */
	public enum LinkKind {
		EMAIL_CAMPAIGN, PAID_CAMPAIGN, CONTENT
	}

	/** Leads of one campaign or content item in a month. */
	public record LinkCount(LinkKind kind, Long id, String name, long leads) {
	}

	/** Lead counts per month and source from {@code from} to {@code to}, optionally for one owner or one source. */
	public Map<MarketingPeriod, Map<LeadSource, Long>> monthly(MarketingPeriod from, MarketingPeriod to, Long ownerId,
			LeadSource source) {
		MapSqlParameterSource params = range(from, to, ownerId);
		String where = filters(ownerId);
		if (source != null) {
			where += " and l.source = :source";
			params.addValue("source", source.name());
		}
		Map<MarketingPeriod, Map<LeadSource, Long>> counts = new HashMap<>();
		jdbc.query("select year(l.lead_date) as y, month(l.lead_date) as m, l.source, count(*) as leads"
				+ " from marketing_leads l where " + where + " group by year(l.lead_date), month(l.lead_date), l.source",
				params, rs -> {
					counts.computeIfAbsent(new MarketingPeriod(rs.getInt("m"), rs.getInt("y")),
							p -> new EnumMap<>(LeadSource.class))
						.put(LeadSource.valueOf(rs.getString("source")), rs.getLong("leads"));
				});
		return counts;
	}

	/** One month's leads per status. */
	public Map<LeadStatus, Long> byStatus(MarketingPeriod period, Long ownerId) {
		Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
		jdbc.query("select l.status, count(*) as leads from marketing_leads l where " + filters(ownerId)
				+ " group by l.status", range(period, period, ownerId), rs -> {
					counts.put(LeadStatus.valueOf(rs.getString("status")), rs.getLong("leads"));
				});
		return counts;
	}

	/** The campaigns and content that brought in the most leads in a month, most first. */
	public List<LinkCount> topLinks(MarketingPeriod period, Long ownerId, int limit) {
		MapSqlParameterSource params = range(period, period, ownerId).addValue("limit", limit);
		List<LinkCount> links = new ArrayList<>();
		jdbc.query("""
				select x.kind, x.id, x.name, x.leads from (
				  select 'EMAIL_CAMPAIGN' as kind, e.id, e.name, count(*) as leads from marketing_leads l
				    join email_campaigns e on e.id = l.email_campaign_id where %1$s group by e.id, e.name
				  union all
				  select 'PAID_CAMPAIGN', p.id, p.name, count(*) from marketing_leads l
				    join paid_campaigns p on p.id = l.paid_campaign_id where %1$s group by p.id, p.name
				  union all
				  select 'CONTENT', c.id, c.title, count(*) from marketing_leads l
				    join content_items c on c.id = l.content_item_id where %1$s group by c.id, c.title
				) x order by x.leads desc, x.name limit :limit
				""".formatted(filters(ownerId)), params, rs -> {
			links.add(new LinkCount(LinkKind.valueOf(rs.getString("kind")), rs.getLong("id"), rs.getString("name"),
					rs.getLong("leads")));
		});
		return links;
	}

	private static MapSqlParameterSource range(MarketingPeriod from, MarketingPeriod to, Long ownerId) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", from.firstDay())
			.addValue("to", to.lastDay());
		if (ownerId != null) {
			params.addValue("ownerId", ownerId);
		}
		return params;
	}

	/** By lead date (an index range scan), optionally for one owner. */
	private static String filters(Long ownerId) {
		return "l.lead_date between :from and :to" + (ownerId == null ? "" : " and l.owner_id = :ownerId");
	}

}
