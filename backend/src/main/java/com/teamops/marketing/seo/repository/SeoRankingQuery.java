package com.teamops.marketing.seo.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.entity.RankingSource;
import com.teamops.marketing.seo.service.KeywordStanding;

import lombok.RequiredArgsConstructor;

/**
 * Keyword standings for a month: one query that joins each keyword to its history rows for the month and the month
 * before, for a whole page of keywords or pages at once (never one query per keyword).
 */
@Repository
@RequiredArgsConstructor
public class SeoRankingQuery {

	private static final String SQL = """
			select k.id as keyword_id, k.page_id, k.status,
			  cur.id as cur_id, cur.ranking_position as cur_position, cur.version as cur_version,
			  cur.search_volume as cur_volume, cur.notes as cur_notes, cur.source as cur_source,
			  cur.updated_at as cur_updated_at,
			  prev.id as prev_id, prev.ranking_position as prev_position
			from marketing_keywords k
			left join keyword_ranking_history cur
			  on cur.keyword_id = k.id and cur.ranking_year = :year and cur.ranking_month = :month
			left join keyword_ranking_history prev
			  on prev.keyword_id = k.id and prev.ranking_year = :previousYear and prev.ranking_month = :previousMonth
			where %s
			""";

	private final NamedParameterJdbcTemplate jdbc;

	/**
	 * The month's history row, so it can be shown and corrected; {@code null} in {@link Row} when nothing was
	 * recorded for the month.
	 */
	public record Entry(Long id, Integer version, Integer searchVolume, String notes, RankingSource source,
			Instant updatedAt) {
	}

	public record Row(Long keywordId, Long pageId, KeywordStatus status, KeywordStanding standing, Entry entry) {
	}

	public List<Row> forKeywords(Collection<Long> keywordIds, MarketingPeriod period) {
		if (keywordIds.isEmpty()) {
			return List.of();
		}
		return query("k.id in (:ids)", params(period).addValue("ids", keywordIds));
	}

	public List<Row> forPages(Collection<Long> pageIds, MarketingPeriod period) {
		if (pageIds.isEmpty()) {
			return List.of();
		}
		return query("k.page_id in (:ids)", params(period).addValue("ids", pageIds));
	}

	/** Every keyword that is not archived, optionally for one page and/or owner (the monthly report). */
	public List<Row> forReport(Long pageId, Long ownerId, MarketingPeriod period) {
		MapSqlParameterSource params = params(period);
		StringBuilder where = new StringBuilder("k.status <> 'ARCHIVED'");
		if (pageId != null) {
			where.append(" and k.page_id = :pageId");
			params.addValue("pageId", pageId);
		}
		if (ownerId != null) {
			where.append(" and k.owner_id = :ownerId");
			params.addValue("ownerId", ownerId);
		}
		return query(where.toString(), params);
	}

	/**
	 * Keywords (not archived) recorded at positions 1–10 per month from {@code from} to {@code to}, optionally for one
	 * owner: the Keywords in Top 10 target and the dashboard trend. Months with no rankings recorded are missing.
	 */
	public Map<MarketingPeriod, Long> top10ByMonth(MarketingPeriod from, MarketingPeriod to, Long ownerId) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", from.year() * 12 + from.month())
			.addValue("to", to.year() * 12 + to.month());
		String owner = "";
		if (ownerId != null) {
			owner = " and k.owner_id = :ownerId";
			params.addValue("ownerId", ownerId);
		}
		Map<MarketingPeriod, Long> counts = new HashMap<>();
		jdbc.query("""
				select h.ranking_year as y, h.ranking_month as m,
				  sum(case when h.ranking_position between 1 and 10 then 1 else 0 end) as top10
				from keyword_ranking_history h
				join marketing_keywords k on k.id = h.keyword_id
				where k.status <> 'ARCHIVED' and h.ranking_year * 12 + h.ranking_month between :from and :to%s
				group by h.ranking_year, h.ranking_month
				""".formatted(owner), params, rs -> {
			counts.put(new MarketingPeriod(rs.getInt("m"), rs.getInt("y")), rs.getLong("top10"));
		});
		return counts;
	}

	private static MapSqlParameterSource params(MarketingPeriod period) {
		MarketingPeriod previous = period.previous();
		return new MapSqlParameterSource().addValue("year", period.year())
			.addValue("month", period.month())
			.addValue("previousYear", previous.year())
			.addValue("previousMonth", previous.month());
	}

	private List<Row> query(String where, MapSqlParameterSource params) {
		List<Row> rows = new ArrayList<>();
		jdbc.query(SQL.formatted(where), params, rs -> {
			Long currentId = rs.getObject("cur_id", Long.class);
			// DATETIME columns hold UTC (the JDBC session time zone is UTC).
			LocalDateTime updatedAt = rs.getObject("cur_updated_at", LocalDateTime.class);
			Entry entry = currentId == null ? null
					: new Entry(currentId, rs.getObject("cur_version", Integer.class),
							rs.getObject("cur_volume", Integer.class), rs.getString("cur_notes"),
							RankingSource.valueOf(rs.getString("cur_source")),
							updatedAt == null ? null : updatedAt.toInstant(ZoneOffset.UTC));
			rows.add(new Row(rs.getLong("keyword_id"), rs.getLong("page_id"),
					KeywordStatus.valueOf(rs.getString("status")),
					KeywordStanding.of(currentId != null, rs.getObject("cur_position", Integer.class),
							rs.getObject("prev_id") != null, rs.getObject("prev_position", Integer.class)),
					entry));
		});
		return rows;
	}

}
