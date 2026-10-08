package com.teamops.marketing.seo.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.seo.entity.KeywordStatus;
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
			  cur.id as cur_id, cur.ranking_position as cur_position,
			  prev.id as prev_id, prev.ranking_position as prev_position
			from marketing_keywords k
			left join keyword_ranking_history cur
			  on cur.keyword_id = k.id and cur.ranking_year = :year and cur.ranking_month = :month
			left join keyword_ranking_history prev
			  on prev.keyword_id = k.id and prev.ranking_year = :previousYear and prev.ranking_month = :previousMonth
			where %s
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public record Row(Long keywordId, Long pageId, KeywordStatus status, KeywordStanding standing) {
	}

	public List<Row> forKeywords(Collection<Long> keywordIds, MarketingPeriod period) {
		return query("k.id in (:ids)", keywordIds, period);
	}

	public List<Row> forPages(Collection<Long> pageIds, MarketingPeriod period) {
		return query("k.page_id in (:ids)", pageIds, period);
	}

	private List<Row> query(String where, Collection<Long> ids, MarketingPeriod period) {
		if (ids.isEmpty()) {
			return List.of();
		}
		MarketingPeriod previous = period.previous();
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", ids)
			.addValue("year", period.year())
			.addValue("month", period.month())
			.addValue("previousYear", previous.year())
			.addValue("previousMonth", previous.month());
		List<Row> rows = new ArrayList<>();
		jdbc.query(SQL.formatted(where), params, rs -> {
			rows.add(new Row(rs.getLong("keyword_id"), rs.getLong("page_id"),
					KeywordStatus.valueOf(rs.getString("status")),
					KeywordStanding.of(rs.getObject("cur_id") != null, rs.getObject("cur_position", Integer.class),
							rs.getObject("prev_id") != null, rs.getObject("prev_position", Integer.class))));
		});
		return rows;
	}

}
