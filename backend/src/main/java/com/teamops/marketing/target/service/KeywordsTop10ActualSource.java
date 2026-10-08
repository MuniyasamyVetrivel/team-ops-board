package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

import lombok.RequiredArgsConstructor;

/**
 * Keywords in Top 10: keywords (not archived) recorded at positions 1–10 for the month, the same count as the SEO
 * monthly summary. A month with no rankings recorded at all has no actual yet.
 */
@Component
@RequiredArgsConstructor
class KeywordsTop10ActualSource implements TargetActualSource {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public ActualSource source() {
		return ActualSource.KEYWORDS_TOP10;
	}

	@Override
	public Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		Map<MarketingPeriod, BigDecimal> actuals = new HashMap<>();
		jdbc.query("""
				select h.ranking_year as y, h.ranking_month as m,
				  sum(case when h.ranking_position between 1 and 10 then 1 else 0 end) as top10
				from keyword_ranking_history h
				join marketing_keywords k on k.id = h.keyword_id
				where k.status <> 'ARCHIVED' and h.ranking_year * 12 + h.ranking_month between :from and :to
				group by h.ranking_year, h.ranking_month
				""", new MapSqlParameterSource().addValue("from", key(from)).addValue("to", key(to)), rs -> {
			actuals.put(new MarketingPeriod(rs.getInt("m"), rs.getInt("y")), BigDecimal.valueOf(rs.getLong("top10")));
		});
		return actuals;
	}

	private static int key(MarketingPeriod period) {
		return period.year() * 12 + period.month();
	}

}
