package com.teamops.marketing.content.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.content.entity.ContentStatus;
import com.teamops.marketing.content.entity.ContentType;

import lombok.RequiredArgsConstructor;

/**
 * Content counts from grouped queries. Live content (PUBLISHED or UPDATED) counts in the month of its publication
 * date; blogs planned for a month count by planned date whatever their status; refreshes by refreshed date; leads by
 * their lead date. {@code ownerId} filters by the content's owner.
 */
@Repository
@RequiredArgsConstructor
public class ContentQuery {

	private static final String LIVE = "c.status in ('PUBLISHED', 'UPDATED')";

	/** One month's figures. */
	public record MonthFigures(long plannedBlogs, long publishedBlogs, long publishedAll, long refreshed, long leads) {

		public static final MonthFigures ZERO = new MonthFigures(0, 0, 0, 0, 0);

		MonthFigures plus(String kind, long n) {
			return switch (kind) {
				case "PLANNED" -> new MonthFigures(plannedBlogs + n, publishedBlogs, publishedAll, refreshed, leads);
				case "PUBLISHED_BLOG" -> new MonthFigures(plannedBlogs, publishedBlogs + n, publishedAll, refreshed, leads);
				case "PUBLISHED_ALL" -> new MonthFigures(plannedBlogs, publishedBlogs, publishedAll + n, refreshed, leads);
				case "REFRESHED" -> new MonthFigures(plannedBlogs, publishedBlogs, publishedAll, refreshed + n, leads);
				case "LEADS" -> new MonthFigures(plannedBlogs, publishedBlogs, publishedAll, refreshed, leads + n);
				default -> throw new IllegalStateException(kind);
			};
		}

	}

	/** Content that brought in leads in a month. */
	public record ContentLeads(Long id, String title, ContentType contentType, String url, Integer organicTraffic,
			Integer ctaClicks, long leads) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	/** Figures per month from {@code from} to {@code to}; months without activity are missing. */
	public Map<MarketingPeriod, MonthFigures> monthly(MarketingPeriod from, MarketingPeriod to, Long ownerId) {
		String owner = owner(ownerId);
		String sql = String.join(" union all ",
				group("PLANNED", "c.planned_date", "content_items c where c.content_type = 'BLOG' and ", owner),
				group("PUBLISHED_BLOG", "c.publication_date", "content_items c where c.content_type = 'BLOG' and " + LIVE + " and ", owner),
				group("PUBLISHED_ALL", "c.publication_date", "content_items c where " + LIVE + " and ", owner),
				group("REFRESHED", "c.refreshed_date", "content_items c where c.status = 'UPDATED' and ", owner),
				group("LEADS", "l.lead_date", "marketing_leads l join content_items c on c.id = l.content_item_id where ", owner));
		Map<MarketingPeriod, MonthFigures> figures = new HashMap<>();
		jdbc.query(sql, params(from, to, ownerId), rs -> {
			MarketingPeriod period = new MarketingPeriod(rs.getInt("m"), rs.getInt("y"));
			MonthFigures before = figures.getOrDefault(period, MonthFigures.ZERO);
			figures.put(period, before.plus(rs.getString("kind"), rs.getLong("n")));
		});
		return figures;
	}

	/** Live content published in the month, per type. */
	public Map<ContentType, Long> publishedByType(MarketingPeriod period, Long ownerId) {
		Map<ContentType, Long> counts = new EnumMap<>(ContentType.class);
		jdbc.query("select c.content_type, count(*) as n from content_items c where " + LIVE
				+ " and c.publication_date between :from and :to" + owner(ownerId) + " group by c.content_type",
				params(period, period, ownerId), rs -> {
					counts.put(ContentType.valueOf(rs.getString("content_type")), rs.getLong("n"));
				});
		return counts;
	}

	/** Content per current status (the pipeline today), optionally for one owner. */
	public Map<ContentStatus, Long> byStatus(Long ownerId) {
		Map<ContentStatus, Long> counts = new EnumMap<>(ContentStatus.class);
		MapSqlParameterSource params = new MapSqlParameterSource();
		if (ownerId != null) {
			params.addValue("ownerId", ownerId);
		}
		jdbc.query("select c.status, count(*) as n from content_items c where 1 = 1" + owner(ownerId)
				+ " group by c.status", params, rs -> {
					counts.put(ContentStatus.valueOf(rs.getString("status")), rs.getLong("n"));
				});
		return counts;
	}

	/** The content that brought in the most leads in a month, most first. */
	public List<ContentLeads> topByLeads(MarketingPeriod period, Long ownerId, int limit) {
		List<ContentLeads> rows = new ArrayList<>();
		jdbc.query("""
				select c.id, c.title, c.content_type, c.url, c.organic_traffic, c.cta_clicks, count(*) as n
				from marketing_leads l join content_items c on c.id = l.content_item_id
				where l.lead_date between :from and :to%s
				group by c.id, c.title, c.content_type, c.url, c.organic_traffic, c.cta_clicks
				order by n desc, c.title limit :limit
				""".formatted(owner(ownerId)), params(period, period, ownerId).addValue("limit", limit), rs -> {
			rows.add(new ContentLeads(rs.getLong("id"), rs.getString("title"), ContentType.valueOf(rs.getString("content_type")),
					rs.getString("url"), rs.getObject("organic_traffic", Integer.class),
					rs.getObject("cta_clicks", Integer.class), rs.getLong("n")));
		});
		return rows;
	}

	/** All-time leads per content item, for a page of the list. */
	public Map<Long, Long> leadCounts(Collection<Long> ids) {
		Map<Long, Long> counts = new HashMap<>();
		if (ids.isEmpty()) {
			return counts;
		}
		jdbc.query("select l.content_item_id as id, count(*) as n from marketing_leads l where l.content_item_id in (:ids)"
				+ " group by l.content_item_id", new MapSqlParameterSource("ids", ids), rs -> {
					counts.put(rs.getLong("id"), rs.getLong("n"));
				});
		return counts;
	}

	/** Counts by year and month of {@code dateColumn} within the range, labelled {@code kind}. */
	private static String group(String kind, String dateColumn, String fromWhere, String owner) {
		return "select '%1$s' as kind, year(%2$s) as y, month(%2$s) as m, count(*) as n from %3$s%2$s between :from and :to%4$s group by year(%2$s), month(%2$s)"
			.formatted(kind, dateColumn, fromWhere, owner);
	}

	private static String owner(Long ownerId) {
		return ownerId == null ? "" : " and c.owner_id = :ownerId";
	}

	private static MapSqlParameterSource params(MarketingPeriod from, MarketingPeriod to, Long ownerId) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", from.firstDay())
			.addValue("to", to.lastDay());
		if (ownerId != null) {
			params.addValue("ownerId", ownerId);
		}
		return params;
	}

}
